"""
Unit tests for relevance score calculation edge cases.
"""

import pytest
from datetime import datetime, timedelta
from unittest.mock import Mock, patch
from src.services.recommendation_service import RecommendationService


@pytest.mark.unit
class TestRelevanceScoreUnit:
    """Unit tests for specific edge cases in score calculation."""
    
    @pytest.mark.asyncio
    async def test_post_not_found_returns_zero(self):
        """Post that doesn't exist should return score of 0."""
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            mock_cache.get.return_value = None
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            mock_cursor.fetchone.return_value = None
            
            score = await service.calculate_relevance_score("user1", "nonexistent-post")
            
            assert score == 0.0
    
    @pytest.mark.asyncio
    async def test_cached_score_is_used(self):
        """When score is cached, database should not be queried."""
        service = RecommendationService()
        cached_score = "42.5"
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            mock_cache.get.return_value = cached_score
            
            score = await service.calculate_relevance_score("user1", "post1")
            
            # Database should not be queried
            assert not mock_db.get_cursor.called
            assert score == 42.5
    
    @pytest.mark.asyncio
    async def test_invalid_cached_score_recalculates(self):
        """Invalid cached score should trigger recalculation."""
        service = RecommendationService()
        
        post_data = {
            'likes_count': 10,
            'comments_count': 5,
            'shares_count': 2,
            'created_at': datetime.now() - timedelta(hours=1)
        }
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            # Return invalid cache value
            mock_cache.get.return_value = "invalid"
            
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            mock_cursor.fetchone.return_value = post_data
            
            score = await service.calculate_relevance_score("user1", "post1")
            
            # Should have queried database
            assert mock_db.get_cursor.called
            assert score > 0
    
    @pytest.mark.asyncio
    async def test_null_engagement_counts_treated_as_zero(self):
        """Null engagement counts should be treated as zero."""
        service = RecommendationService()
        
        post_data = {
            'likes_count': None,
            'comments_count': None,
            'shares_count': None,
            'created_at': datetime.now()
        }
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            mock_cache.get.return_value = None
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            mock_cursor.fetchone.return_value = post_data
            
            score = await service.calculate_relevance_score("user1", "post1")
            
            assert score == 0.0
    
    @pytest.mark.asyncio
    async def test_very_old_post_has_low_score(self):
        """Very old posts should have very low scores due to time decay."""
        service = RecommendationService()
        
        # Post from 30 days ago with high engagement
        post_data = {
            'likes_count': 1000,
            'comments_count': 500,
            'shares_count': 200,
            'created_at': datetime.now() - timedelta(days=30)
        }
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            mock_cache.get.return_value = None
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            mock_cursor.fetchone.return_value = post_data
            
            score = await service.calculate_relevance_score("user1", "post1")
            
            # Score should be very low (close to 0) due to time decay
            # Even with high engagement, 30 days old should decay significantly
            assert score < 10, f"30-day old post should have very low score, got {score}"
    
    @pytest.mark.asyncio
    async def test_brand_new_post_has_full_decay_factor(self):
        """Brand new posts should have time decay factor close to 1."""
        service = RecommendationService()
        
        # Post from 1 minute ago
        post_data = {
            'likes_count': 10,
            'comments_count': 5,
            'shares_count': 2,
            'created_at': datetime.now() - timedelta(minutes=1)
        }
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            mock_cache.get.return_value = None
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            mock_cursor.fetchone.return_value = post_data
            
            score = await service.calculate_relevance_score("user1", "post1")
            
            # Calculate expected engagement score
            expected_engagement = (
                10 * service.weight_likes +
                5 * service.weight_comments +
                2 * service.weight_shares
            )
            
            # Score should be very close to engagement score (decay ≈ 1)
            assert score > expected_engagement * 0.99, (
                f"Brand new post should have score close to engagement score. "
                f"Expected ~{expected_engagement}, got {score}"
            )
