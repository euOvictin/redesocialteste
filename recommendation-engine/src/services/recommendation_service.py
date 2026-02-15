from typing import Optional, List
from datetime import datetime, timedelta
import json
import logging
import math
from src.database import db, cache
from src.config import settings
from src.models import Post, FeedResponse

logger = logging.getLogger(__name__)


class RecommendationService:
    """Service for generating personalized feeds and calculating relevance scores."""
    
    def __init__(self):
        self.posts_per_page = settings.posts_per_page
        self.feed_cache_ttl = settings.feed_cache_ttl
        self.score_cache_ttl = settings.score_cache_ttl
        self.weight_likes = settings.engagement_weight_likes
        self.weight_comments = settings.engagement_weight_comments
        self.weight_shares = settings.engagement_weight_shares
        self.time_decay_hours = settings.time_decay_hours
    
    async def generate_feed(
        self,
        user_id: str,
        cursor: Optional[str] = None,
        limit: int = 20
    ) -> FeedResponse:
        """
        Generate personalized feed for a user.
        
        Returns posts from followed users ordered by relevance score and timestamp.
        Implements cursor-based pagination with caching.
        
        Args:
            user_id: User ID to generate feed for
            cursor: Pagination cursor (post_id of last item from previous page)
            limit: Number of posts per page (default: 20)
            
        Returns:
            FeedResponse with posts, cursor, and has_more flag
        """
        # Limit to configured posts per page
        limit = min(limit, self.posts_per_page)
        
        # Check cache first (only for first page)
        if cursor is None:
            cache_key = f"feed:{user_id}"
            cached_feed = cache.get(cache_key)
            if cached_feed:
                try:
                    feed_data = json.loads(cached_feed)
                    # Return cached first page
                    posts = feed_data['posts'][:limit]
                    has_more = len(feed_data['posts']) > limit
                    next_cursor = posts[-1]['id'] if has_more else None
                    
                    return FeedResponse(
                        posts=[Post(**p) for p in posts],
                        cursor=next_cursor,
                        has_more=has_more
                    )
                except (json.JSONDecodeError, KeyError, IndexError):
                    # Invalid cache, continue to regenerate
                    pass
        
        # Get list of users that this user follows
        with db.get_cursor() as db_cursor:
            db_cursor.execute("""
                SELECT following_id
                FROM followers
                WHERE follower_id = %s
            """, (user_id,))
            
            following_rows = db_cursor.fetchall()
            
            if not following_rows:
                # User doesn't follow anyone, return empty feed
                # (trending posts handled by separate endpoint)
                return FeedResponse(posts=[], cursor=None, has_more=False)
            
            following_ids = [row['following_id'] for row in following_rows]
            
            # Build query for posts from followed users
            if cursor:
                # Pagination: get posts after cursor
                db_cursor.execute("""
                    SELECT 
                        pm.post_id,
                        pm.user_id,
                        pm.likes_count,
                        pm.comments_count,
                        pm.shares_count,
                        pm.created_at
                    FROM post_metadata pm
                    WHERE pm.user_id = ANY(%s)
                        AND pm.post_id > %s
                    ORDER BY pm.created_at DESC
                    LIMIT %s
                """, (following_ids, cursor, limit + 1))
            else:
                # First page: get most recent posts
                db_cursor.execute("""
                    SELECT 
                        pm.post_id,
                        pm.user_id,
                        pm.likes_count,
                        pm.comments_count,
                        pm.shares_count,
                        pm.created_at
                    FROM post_metadata pm
                    WHERE pm.user_id = ANY(%s)
                    ORDER BY pm.created_at DESC
                    LIMIT %s
                """, (following_ids, settings.max_feed_size))
            
            post_rows = db_cursor.fetchall()
        
        if not post_rows:
            return FeedResponse(posts=[], cursor=None, has_more=False)
        
        # Calculate relevance scores for all posts
        posts_with_scores = []
        for row in post_rows:
            # Calculate score
            engagement_score = (
                (row['likes_count'] or 0) * self.weight_likes +
                (row['comments_count'] or 0) * self.weight_comments +
                (row['shares_count'] or 0) * self.weight_shares
            )
            
            # Calculate time decay
            created_at = row['created_at']
            if isinstance(created_at, str):
                created_at = datetime.fromisoformat(created_at.replace('Z', '+00:00'))
            
            hours_since_post = (datetime.now() - created_at).total_seconds() / 3600
            time_decay = math.exp(-hours_since_post / self.time_decay_hours)
            
            relevance_score = engagement_score * time_decay
            
            # Fetch full post content from MongoDB (would be done via API call in production)
            # For now, create Post object with available data
            post = Post(
                id=row['post_id'],
                user_id=row['user_id'],
                content="",  # Would fetch from MongoDB
                likes_count=row['likes_count'] or 0,
                comments_count=row['comments_count'] or 0,
                shares_count=row['shares_count'] or 0,
                created_at=created_at,
                relevance_score=relevance_score
            )
            
            posts_with_scores.append(post)
        
        # Sort by relevance score (descending) and timestamp (descending)
        posts_with_scores.sort(
            key=lambda p: (p.relevance_score, p.created_at),
            reverse=True
        )
        
        # Cache the feed (first page only)
        if cursor is None and len(posts_with_scores) > 0:
            cache_key = f"feed:{user_id}"
            cache_data = {
                'posts': [p.model_dump(mode='json') for p in posts_with_scores[:settings.max_feed_size]]
            }
            cache.set(cache_key, json.dumps(cache_data, default=str), self.feed_cache_ttl)
        
        # Apply pagination
        posts_page = posts_with_scores[:limit]
        has_more = len(posts_with_scores) > limit
        next_cursor = posts_page[-1].id if has_more and posts_page else None
        
        return FeedResponse(
            posts=posts_page,
            cursor=next_cursor,
            has_more=has_more
        )
    
    async def calculate_relevance_score(
        self,
        user_id: str,
        post_id: str
    ) -> float:
        """
        Calculate relevance score for a post based on engagement and time decay.
        
        Score = (likes × W_likes + comments × W_comments + shares × W_shares) × time_decay
        Where time_decay = e^(-hours_since_post / TIME_DECAY_HOURS)
        
        Args:
            user_id: User ID (for future personalization)
            post_id: Post ID to calculate score for
            
        Returns:
            Relevance score as a float
        """
        # Check cache first
        cache_key = f"score:{post_id}"
        cached_score = cache.get(cache_key)
        if cached_score is not None:
            try:
                return float(cached_score)
            except (ValueError, TypeError):
                pass
        
        # Fetch post data from database
        with db.get_cursor() as cursor:
            cursor.execute("""
                SELECT 
                    likes_count,
                    comments_count,
                    shares_count,
                    created_at
                FROM post_metadata
                WHERE post_id = %s
            """, (post_id,))
            
            result = cursor.fetchone()
            
            if not result:
                logger.warning(f"Post {post_id} not found in database")
                return 0.0
            
            likes_count = result['likes_count'] or 0
            comments_count = result['comments_count'] or 0
            shares_count = result['shares_count'] or 0
            created_at = result['created_at']
        
        # Calculate engagement score
        engagement_score = (
            likes_count * self.weight_likes +
            comments_count * self.weight_comments +
            shares_count * self.weight_shares
        )
        
        # Calculate time decay
        now = datetime.now()
        if isinstance(created_at, str):
            created_at = datetime.fromisoformat(created_at.replace('Z', '+00:00'))
        
        hours_since_post = (now - created_at).total_seconds() / 3600
        time_decay = math.exp(-hours_since_post / self.time_decay_hours)
        
        # Calculate final relevance score
        relevance_score = engagement_score * time_decay
        
        # Cache the score
        cache.set(cache_key, str(relevance_score), self.score_cache_ttl)
        
        logger.debug(
            f"Calculated score for post {post_id}: "
            f"engagement={engagement_score}, decay={time_decay:.4f}, "
            f"final={relevance_score:.4f}"
        )
        
        return relevance_score
    
    async def get_trending_posts(self, limit: int = 20) -> FeedResponse:
        """
        Get trending posts for users without followers.
        
        Returns popular posts based on global relevance scores.
        
        Args:
            limit: Number of posts to return
            
        Returns:
            FeedResponse with trending posts
        """
        # Check cache first
        cache_key = "feed:trending"
        cached_trending = cache.get(cache_key)
        if cached_trending:
            try:
                feed_data = json.loads(cached_trending)
                posts = feed_data['posts'][:limit]
                return FeedResponse(
                    posts=[Post(**p) for p in posts],
                    cursor=None,
                    has_more=False
                )
            except (json.JSONDecodeError, KeyError):
                pass
        
        # Get recent posts with high engagement
        with db.get_cursor() as db_cursor:
            # Get posts from last 7 days with highest engagement
            seven_days_ago = datetime.now() - timedelta(days=7)
            
            db_cursor.execute("""
                SELECT 
                    pm.post_id,
                    pm.user_id,
                    pm.likes_count,
                    pm.comments_count,
                    pm.shares_count,
                    pm.created_at
                FROM post_metadata pm
                WHERE pm.created_at >= %s
                ORDER BY 
                    (pm.likes_count + pm.comments_count * 2 + pm.shares_count * 3) DESC,
                    pm.created_at DESC
                LIMIT %s
            """, (seven_days_ago, limit * 2))
            
            post_rows = db_cursor.fetchall()
        
        if not post_rows:
            return FeedResponse(posts=[], cursor=None, has_more=False)
        
        # Calculate relevance scores
        posts_with_scores = []
        for row in post_rows:
            engagement_score = (
                (row['likes_count'] or 0) * self.weight_likes +
                (row['comments_count'] or 0) * self.weight_comments +
                (row['shares_count'] or 0) * self.weight_shares
            )
            
            created_at = row['created_at']
            if isinstance(created_at, str):
                created_at = datetime.fromisoformat(created_at.replace('Z', '+00:00'))
            
            hours_since_post = (datetime.now() - created_at).total_seconds() / 3600
            time_decay = math.exp(-hours_since_post / self.time_decay_hours)
            
            relevance_score = engagement_score * time_decay
            
            post = Post(
                id=row['post_id'],
                user_id=row['user_id'],
                content="",
                likes_count=row['likes_count'] or 0,
                comments_count=row['comments_count'] or 0,
                shares_count=row['shares_count'] or 0,
                created_at=created_at,
                relevance_score=relevance_score
            )
            
            posts_with_scores.append(post)
        
        # Sort by relevance score
        posts_with_scores.sort(key=lambda p: p.relevance_score, reverse=True)
        
        # Cache trending posts
        cache_data = {
            'posts': [p.model_dump(mode='json') for p in posts_with_scores[:limit]]
        }
        cache.set(cache_key, json.dumps(cache_data, default=str), self.feed_cache_ttl)
        
        return FeedResponse(
            posts=posts_with_scores[:limit],
            cursor=None,
            has_more=False
        )
    
    async def invalidate_user_cache(self, user_id: str):
        """
        Invalidate feed cache for a user.
        
        Args:
            user_id: User ID whose cache should be invalidated
        """
        cache_key = f"feed:{user_id}"
        cache.delete(cache_key)
        logger.info(f"Invalidated feed cache for user {user_id}")
    
    async def invalidate_followers_cache(self, author_id: str):
        """
        Invalidate feed cache for all followers of a user.
        
        Called when a user creates a new post.
        
        Args:
            author_id: User ID who created the post
        """
        # Get all followers of the author
        with db.get_cursor() as db_cursor:
            db_cursor.execute("""
                SELECT follower_id
                FROM followers
                WHERE following_id = %s
            """, (author_id,))
            
            follower_rows = db_cursor.fetchall()
        
        # Invalidate cache for each follower
        invalidated_count = 0
        for row in follower_rows:
            follower_id = row['follower_id']
            cache_key = f"feed:{follower_id}"
            cache.delete(cache_key)
            invalidated_count += 1
        
        logger.info(f"Invalidated feed cache for {invalidated_count} followers of user {author_id}")
        
        return invalidated_count
    
    async def update_score_from_interaction(
        self,
        post_id: str,
        interaction_type: str
    ):
        """
        Update relevance score based on interaction event.
        
        Invalidates the cached score so it will be recalculated on next request.
        
        Args:
            post_id: Post ID that received the interaction
            interaction_type: Type of interaction (like.created, comment.created, share.created)
        """
        # Invalidate the score cache for this post
        cache_key = f"score:{post_id}"
        cache.delete(cache_key)
        
        logger.info(f"Invalidated score cache for post {post_id} due to {interaction_type}")
        
        # Also invalidate trending cache as engagement changed
        cache.delete("feed:trending")
