"""
Property-based tests for relevance score calculation.

Feature: rede-social-brasileira
Property 18: Score de relevância reflete engajamento
**Validates: Requirements 4.2**
"""

import pytest
from hypothesis import given, strategies as st, settings, assume
from datetime import datetime, timedelta
from unittest.mock import Mock, patch
from src.services.recommendation_service import RecommendationService
from src.config import settings as app_settings


# Generators for test data
@st.composite
def post_data(draw):
    """Generate valid post data with engagement metrics."""
    likes = draw(st.integers(min_value=0, max_value=10000))
    comments = draw(st.integers(min_value=0, max_value=1000))
    shares = draw(st.integers(min_value=0, max_value=1000))
    
    # Generate created_at within last 7 days
    hours_ago = draw(st.integers(min_value=0, max_value=168))  # 7 days
    created_at = datetime.now() - timedelta(hours=hours_ago)
    
    return {
        'likes_count': likes,
        'comments_count': comments,
        'shares_count': shares,
        'created_at': created_at,
        'hours_ago': hours_ago
    }


@pytest.mark.property
class TestRelevanceScoreProperties:
    """Property-based tests for relevance score calculation."""
    
    @given(
        post1=post_data(),
        post2=post_data()
    )
    @settings(max_examples=100)
    async def test_property_18_higher_engagement_higher_score(self, post1, post2):
        """
        Property 18: Score de relevância reflete engajamento
        
        For any two posts with the same age, the post with higher engagement
        (likes + comments + shares) should have a higher or equal relevance score.
        
        **Validates: Requirements 4.2**
        """
        # Make posts same age to isolate engagement effect
        post1['created_at'] = datetime.now() - timedelta(hours=24)
        post2['created_at'] = datetime.now() - timedelta(hours=24)
        
        # Calculate engagement scores
        engagement1 = (
            post1['likes_count'] * app_settings.engagement_weight_likes +
            post1['comments_count'] * app_settings.engagement_weight_comments +
            post1['shares_count'] * app_settings.engagement_weight_shares
        )
        
        engagement2 = (
            post2['likes_count'] * app_settings.engagement_weight_likes +
            post2['comments_count'] * app_settings.engagement_weight_comments +
            post2['shares_count'] * app_settings.engagement_weight_shares
        )
        
        # Skip if engagements are equal (no ordering expected)
        assume(engagement1 != engagement2)
        
        service = RecommendationService()
        
        # Mock database calls
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            # Mock cache misses
            mock_cache.get.return_value = None
            
            # Mock cursor context manager
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            
            # Calculate score for post1
            mock_cursor.fetchone.return_value = post1
            score1 = await service.calculate_relevance_score("user1", "post1")
            
            # Calculate score for post2
            mock_cursor.fetchone.return_value = post2
            score2 = await service.calculate_relevance_score("user1", "post2")
            
            # Verify: higher engagement => higher score
            if engagement1 > engagement2:
                assert score1 > score2, (
                    f"Post with higher engagement should have higher score. "
                    f"Post1 engagement={engagement1}, score={score1}; "
                    f"Post2 engagement={engagement2}, score={score2}"
                )
            else:
                assert score2 > score1, (
                    f"Post with higher engagement should have higher score. "
                    f"Post1 engagement={engagement1}, score={score1}; "
                    f"Post2 engagement={engagement2}, score={score2}"
                )
    
    @given(post=post_data())
    @settings(max_examples=100)
    async def test_score_is_non_negative(self, post):
        """
        Relevance scores should always be non-negative.
        """
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            mock_cache.get.return_value = None
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            mock_cursor.fetchone.return_value = post
            
            score = await service.calculate_relevance_score("user1", "post1")
            
            assert score >= 0, f"Score should be non-negative, got {score}"
    
    @given(
        likes=st.integers(min_value=0, max_value=1000),
        comments=st.integers(min_value=0, max_value=1000),
        shares=st.integers(min_value=0, max_value=1000)
    )
    @settings(max_examples=100)
    async def test_newer_posts_have_higher_decay_factor(self, likes, comments, shares):
        """
        For posts with same engagement, newer posts should have higher scores
        due to time decay factor.
        """
        service = RecommendationService()
        
        # Create two posts with same engagement but different ages
        newer_post = {
            'likes_count': likes,
            'comments_count': comments,
            'shares_count': shares,
            'created_at': datetime.now() - timedelta(hours=1)
        }
        
        older_post = {
            'likes_count': likes,
            'comments_count': comments,
            'shares_count': shares,
            'created_at': datetime.now() - timedelta(hours=48)
        }
        
        # Skip if no engagement (both scores would be 0)
        total_engagement = (
            likes * app_settings.engagement_weight_likes +
            comments * app_settings.engagement_weight_comments +
            shares * app_settings.engagement_weight_shares
        )
        assume(total_engagement > 0)
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            mock_cache.get.return_value = None
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            
            # Calculate score for newer post
            mock_cursor.fetchone.return_value = newer_post
            newer_score = await service.calculate_relevance_score("user1", "post1")
            
            # Calculate score for older post
            mock_cursor.fetchone.return_value = older_post
            older_score = await service.calculate_relevance_score("user1", "post2")
            
            # Newer post should have higher score
            assert newer_score > older_score, (
                f"Newer post should have higher score due to time decay. "
                f"Newer (1h old): {newer_score}, Older (48h old): {older_score}"
            )
    
    @given(post=post_data())
    @settings(max_examples=100)
    async def test_score_caching(self, post):
        """
        Calculated scores should be cached and retrieved from cache on subsequent calls.
        """
        service = RecommendationService()
        post_id = "test-post-123"
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            # First call: cache miss
            mock_cache.get.return_value = None
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            mock_cursor.fetchone.return_value = post
            
            score1 = await service.calculate_relevance_score("user1", post_id)
            
            # Verify cache.set was called
            assert mock_cache.set.called, "Score should be cached after calculation"
            cache_key = f"score:{post_id}"
            mock_cache.set.assert_called_once()
            assert mock_cache.set.call_args[0][0] == cache_key
            
            # Second call: cache hit
            mock_cache.get.return_value = str(score1)
            score2 = await service.calculate_relevance_score("user1", post_id)
            
            # Scores should be identical
            assert score1 == score2, "Cached score should match calculated score"
    
    @given(post=post_data())
    @settings(max_examples=100)
    async def test_zero_engagement_gives_zero_score(self, post):
        """
        Posts with zero engagement should have zero relevance score.
        """
        service = RecommendationService()
        
        # Force zero engagement
        post['likes_count'] = 0
        post['comments_count'] = 0
        post['shares_count'] = 0
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            mock_cache.get.return_value = None
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            mock_cursor.fetchone.return_value = post
            
            score = await service.calculate_relevance_score("user1", "post1")
            
            assert score == 0.0, f"Zero engagement should give zero score, got {score}"
