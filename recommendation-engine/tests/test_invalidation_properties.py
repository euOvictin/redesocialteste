"""
Property-based tests for cache invalidation.

Feature: rede-social-brasileira
Property 19: Novo post invalida cache de seguidores
**Validates: Requirements 4.4**
"""

import pytest
from hypothesis import given, strategies as st, settings, assume
from unittest.mock import Mock, patch
from src.services.recommendation_service import RecommendationService


# Generators for test data
@st.composite
def user_with_followers(draw):
    """Generate a user with a list of followers."""
    user_id = draw(st.text(min_size=1, max_size=20, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'))))
    num_followers = draw(st.integers(min_value=0, max_value=20))
    follower_ids = [
        draw(st.text(min_size=1, max_size=20, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'))))
        for _ in range(num_followers)
    ]
    # Ensure unique follower IDs
    follower_ids = list(set(follower_ids))
    return user_id, follower_ids


@pytest.mark.property
class TestCacheInvalidationProperties:
    """Property-based tests for cache invalidation."""
    
    @given(user_data=user_with_followers())
    @settings(max_examples=100)
    async def test_property_19_new_post_invalidates_followers_cache(self, user_data):
        """
        Property 19: Novo post invalida cache de seguidores
        
        For any user who creates a post, the feed cache of all their followers
        should be invalidated.
        
        **Validates: Requirements 4.4**
        """
        author_id, follower_ids = user_data
        
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            # Mock cursor context manager
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            
            # Mock followers query
            follower_rows = [{'follower_id': fid} for fid in follower_ids]
            mock_cursor.fetchall.return_value = follower_rows
            
            # Invalidate followers' cache
            invalidated_count = await service.invalidate_followers_cache(author_id)
            
            # Verify: cache.delete was called for each follower
            assert invalidated_count == len(follower_ids), (
                f"Expected {len(follower_ids)} cache invalidations, got {invalidated_count}"
            )
            
            # Verify: cache.delete was called with correct keys
            if len(follower_ids) > 0:
                delete_calls = mock_cache.delete.call_args_list
                deleted_keys = [call[0][0] for call in delete_calls]
                
                for follower_id in follower_ids:
                    expected_key = f"feed:{follower_id}"
                    assert expected_key in deleted_keys, (
                        f"Cache key {expected_key} should have been deleted"
                    )
    
    @given(user_id=st.text(min_size=1, max_size=20, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'))))
    @settings(max_examples=100)
    async def test_invalidate_user_cache_deletes_correct_key(self, user_id):
        """
        Invalidating a user's cache should delete the correct cache key.
        """
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.cache') as mock_cache:
            # Invalidate user cache
            await service.invalidate_user_cache(user_id)
            
            # Verify: cache.delete was called with correct key
            expected_key = f"feed:{user_id}"
            mock_cache.delete.assert_called_once_with(expected_key)
    
    @given(
        post_id=st.text(min_size=1, max_size=20, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'))),
        interaction_type=st.sampled_from(['like.created', 'comment.created', 'share.created'])
    )
    @settings(max_examples=100)
    async def test_interaction_invalidates_score_cache(self, post_id, interaction_type):
        """
        Any interaction on a post should invalidate its score cache.
        """
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.cache') as mock_cache:
            # Update score from interaction
            await service.update_score_from_interaction(post_id, interaction_type)
            
            # Verify: score cache was deleted
            expected_key = f"score:{post_id}"
            delete_calls = mock_cache.delete.call_args_list
            deleted_keys = [call[0][0] for call in delete_calls]
            
            assert expected_key in deleted_keys, (
                f"Score cache key {expected_key} should have been deleted after {interaction_type}"
            )
    
    @given(user_data=user_with_followers())
    @settings(max_examples=100)
    async def test_user_with_no_followers_invalidates_zero_caches(self, user_data):
        """
        User with no followers should result in zero cache invalidations.
        """
        author_id, _ = user_data
        
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            
            # Mock: no followers
            mock_cursor.fetchall.return_value = []
            
            # Invalidate followers' cache
            invalidated_count = await service.invalidate_followers_cache(author_id)
            
            # Verify: zero invalidations
            assert invalidated_count == 0, (
                f"User with no followers should have 0 cache invalidations, got {invalidated_count}"
            )
            
            # Verify: cache.delete was not called
            assert not mock_cache.delete.called, "cache.delete should not be called when no followers"
    
    @given(
        num_followers=st.integers(min_value=1, max_value=50)
    )
    @settings(max_examples=100)
    async def test_invalidation_count_matches_follower_count(self, num_followers):
        """
        Number of cache invalidations should exactly match number of followers.
        """
        author_id = "test-author"
        follower_ids = [f"follower-{i}" for i in range(num_followers)]
        
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.db') as mock_db, \
             patch('src.services.recommendation_service.cache') as mock_cache:
            
            mock_cursor = Mock()
            mock_db.get_cursor.return_value.__enter__ = Mock(return_value=mock_cursor)
            mock_db.get_cursor.return_value.__exit__ = Mock(return_value=None)
            
            follower_rows = [{'follower_id': fid} for fid in follower_ids]
            mock_cursor.fetchall.return_value = follower_rows
            
            # Invalidate followers' cache
            invalidated_count = await service.invalidate_followers_cache(author_id)
            
            # Verify: exact match
            assert invalidated_count == num_followers, (
                f"Invalidation count {invalidated_count} should match follower count {num_followers}"
            )
            
            # Verify: cache.delete called exactly num_followers times
            assert mock_cache.delete.call_count == num_followers, (
                f"cache.delete should be called {num_followers} times, was called {mock_cache.delete.call_count} times"
            )
