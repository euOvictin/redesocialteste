"""
Property-based tests for feed generation.

Feature: rede-social-brasileira
Property 17: Feed contém apenas posts de seguidos
Property 20: Paginação retorna 20 posts por página
**Validates: Requirements 4.1, 4.5**
"""

import pytest
from hypothesis import given, strategies as st, settings, assume
from datetime import datetime, timedelta
from unittest.mock import Mock, patch
from src.services.recommendation_service import RecommendationService
from src.config import settings as app_settings


# Generators for test data
@st.composite
def user_with_following(draw):
    """Generate a user with a list of users they follow."""
    user_id = draw(st.text(min_size=1, max_size=20, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'))))
    num_following = draw(st.integers(min_value=1, max_value=10))
    following_ids = [
        draw(st.text(min_size=1, max_size=20, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'))))
        for _ in range(num_following)
    ]
    # Ensure unique following IDs
    following_ids = list(set(following_ids))
    assume(len(following_ids) > 0)
    return user_id, following_ids


@st.composite
def posts_from_users(draw, user_ids, min_posts=1, max_posts=50):
    """Generate posts from specific users."""
    num_posts = draw(st.integers(min_value=min_posts, max_value=max_posts))
    posts = []
    
    for i in range(num_posts):
        author_id = draw(st.sampled_from(user_ids))
        post = {
            'post_id': f"post-{i}",
            'user_id': author_id,
            'likes_count': draw(st.integers(min_value=0, max_value=1000)),
            'comments_count': draw(st.integers(min_value=0, max_value=100)),
            'shares_count': draw(st.integers(min_value=0, max_value=100)),
            'created_at': datetime.now() - timedelta(hours=draw(st.integers(min_value=0, max_value=168)))
        }
        posts.append(post)
    
    return posts


@pytest.mark.property
class TestFeedGenerationProperties:
    """Property-based tests for feed generation."""
    
    @given(user_data=user_with_following())
    @settings(max_examples=100)
    async def test_property_17_feed_contains_only_followed_users(self, user_data):
        """
        Property 17: Feed contém apenas posts de seguidos
        
        For any user, the feed should contain only posts from users they follow.
        
        **Validates: Requirements 4.1**
        """
        user_id, following_ids = user_data
        
        # Generate posts from followed users and some random users
        all_user_ids = following_ids + ['random-user-1', 'random-user-2']
        
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            # Mock cache miss
            mock_cache.get.return_value = None
            
            # Mock cursor context manager
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            
            # Mock followers query
            following_rows = [{'following_id': fid} for fid in following_ids]
            
            # Generate posts (some from followed, some from non-followed)
            posts_data = []
            for i in range(30):
                # Mix of followed and non-followed users
                if i < 20:
                    author_id = following_ids[i % len(following_ids)]
                else:
                    author_id = all_user_ids[i % len(all_user_ids)]
                
                posts_data.append({
                    'post_id': f"post-{i}",
                    'user_id': author_id,
                    'likes_count': 10,
                    'comments_count': 5,
                    'shares_count': 2,
                    'created_at': datetime.now() - timedelta(hours=i)
                })
            
            # Setup mock to return following and posts
            mock_cursor.fetchall.side_effect = [
                following_rows,  # First call: get following
                posts_data       # Second call: get posts
            ]
            
            # Generate feed
            feed = await service.generate_feed(user_id, cursor=None, limit=20)
            
            # Verify: all posts in feed are from followed users
            for post in feed.posts:
                assert post.user_id in following_ids, (
                    f"Feed contains post from non-followed user {post.user_id}. "
                    f"User {user_id} only follows {following_ids}"
                )
    
    @given(
        num_posts=st.integers(min_value=1, max_value=100),
        page_size=st.integers(min_value=1, max_value=50)
    )
    @settings(max_examples=100)
    async def test_property_20_pagination_returns_correct_size(self, num_posts, page_size):
        """
        Property 20: Paginação retorna 20 posts por página
        
        For any feed request, pagination should return at most the requested
        number of posts per page (default 20, max configured limit).
        
        **Validates: Requirements 4.5**
        """
        user_id = "test-user"
        following_ids = ["user1", "user2", "user3"]
        
        # Limit page size to configured maximum
        page_size = min(page_size, app_settings.posts_per_page)
        
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            mock_cache.get.return_value = None
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            
            # Mock followers
            following_rows = [{'following_id': fid} for fid in following_ids]
            
            # Generate posts
            posts_data = []
            for i in range(num_posts):
                posts_data.append({
                    'post_id': f"post-{i}",
                    'user_id': following_ids[i % len(following_ids)],
                    'likes_count': 10,
                    'comments_count': 5,
                    'shares_count': 2,
                    'created_at': datetime.now() - timedelta(hours=i)
                })
            
            mock_cursor.fetchall.side_effect = [
                following_rows,
                posts_data
            ]
            
            # Generate feed with specific page size
            feed = await service.generate_feed(user_id, cursor=None, limit=page_size)
            
            # Verify: returned posts <= page_size
            assert len(feed.posts) <= page_size, (
                f"Feed returned {len(feed.posts)} posts, expected at most {page_size}"
            )
            
            # Verify: if more posts available, has_more should be True
            if num_posts > page_size:
                assert feed.has_more, "has_more should be True when more posts available"
                assert feed.cursor is not None, "cursor should be provided when more posts available"
            else:
                # All posts fit in one page
                assert len(feed.posts) == num_posts
    
    @given(user_data=user_with_following())
    @settings(max_examples=100)
    async def test_feed_uses_cache_when_available(self, user_data):
        """
        Feed should use cached data when available for first page.
        """
        user_id, following_ids = user_data
        
        service = RecommendationService()
        
        # Create cached feed data
        import json
        cached_posts = [
            {
                'id': f'post-{i}',
                'user_id': following_ids[i % len(following_ids)],
                'content': '',
                'likes_count': 10,
                'comments_count': 5,
                'shares_count': 2,
                'created_at': (datetime.now() - timedelta(hours=i)).isoformat(),
                'relevance_score': 50.0
            }
            for i in range(25)
        ]
        
        cache_data = json.dumps({'posts': cached_posts})
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            # Mock cache hit
            mock_cache.get.return_value = cache_data
            
            # Generate feed (first page)
            feed = await service.generate_feed(user_id, cursor=None, limit=20)
            
            # Verify: database should NOT be queried
            assert not mock_db.get_cursor.called, "Database should not be queried when cache is available"
            
            # Verify: returned correct number of posts
            assert len(feed.posts) == 20
            assert feed.has_more == True
    
    @given(user_data=user_with_following())
    @settings(max_examples=100)
    async def test_user_without_following_gets_empty_feed(self, user_data):
        """
        User who doesn't follow anyone should get empty feed.
        """
        user_id, _ = user_data
        
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            mock_cache.get.return_value = None
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            
            # Mock: user follows nobody
            mock_cursor.fetchall.return_value = []
            
            # Generate feed
            feed = await service.generate_feed(user_id, cursor=None, limit=20)
            
            # Verify: empty feed
            assert len(feed.posts) == 0
            assert feed.has_more == False
            assert feed.cursor is None
    
    @given(
        num_posts=st.integers(min_value=5, max_value=50)
    )
    @settings(max_examples=100)
    async def test_feed_ordered_by_relevance(self, num_posts):
        """
        Feed posts should be ordered by relevance score (descending).
        """
        user_id = "test-user"
        following_ids = ["user1", "user2"]
        
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            mock_cache.get.return_value = None
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            
            following_rows = [{'following_id': fid} for fid in following_ids]
            
            # Generate posts with varying engagement
            posts_data = []
            for i in range(num_posts):
                posts_data.append({
                    'post_id': f"post-{i}",
                    'user_id': following_ids[i % len(following_ids)],
                    'likes_count': i * 10,  # Increasing engagement
                    'comments_count': i * 5,
                    'shares_count': i * 2,
                    'created_at': datetime.now() - timedelta(hours=1)  # Same age
                })
            
            mock_cursor.fetchall.side_effect = [
                following_rows,
                posts_data
            ]
            
            # Generate feed
            feed = await service.generate_feed(user_id, cursor=None, limit=num_posts)
            
            # Verify: posts are ordered by relevance score (descending)
            scores = [post.relevance_score for post in feed.posts if post.relevance_score is not None]
            
            if len(scores) > 1:
                for i in range(len(scores) - 1):
                    assert scores[i] >= scores[i + 1], (
                        f"Feed not properly ordered by relevance. "
                        f"Score at position {i} ({scores[i]}) < score at position {i+1} ({scores[i+1]})"
                    )
