"""
Property-based tests for score updates from interactions.

Feature: rede-social-brasileira
Property 31: Interação atualiza score de relevância
**Validates: Requirements 6.7**
"""

import pytest
from hypothesis import given, strategies as st, settings
from unittest.mock import Mock, patch
from src.services.recommendation_service import RecommendationService


@pytest.mark.property
class TestScoreUpdateProperties:
    """Property-based tests for score updates from interactions."""
    
    @given(
        post_id=st.text(min_size=1, max_size=20, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'))),
        interaction_type=st.sampled_from(['like.created', 'comment.created', 'share.created'])
    )
    @settings(max_examples=100)
    async def test_property_31_interaction_updates_score(self, post_id, interaction_type):
        """
        Property 31: Interação atualiza score de relevância
        
        For any post that receives an interaction (like, comment, share),
        the relevance score should be recalculated (cache invalidated).
        
        **Validates: Requirements 6.7**
        """
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.cache') as mock_cache:
            # Update score from interaction
            await service.update_score_from_interaction(post_id, interaction_type)
            
            # Verify: score cache was invalidated
            score_cache_key = f"score:{post_id}"
            delete_calls = mock_cache.delete.call_args_list
            deleted_keys = [call[0][0] for call in delete_calls]
            
            assert score_cache_key in deleted_keys, (
                f"Score cache for post {post_id} should be invalidated after {interaction_type}"
            )
    
    @given(
        post_id=st.text(min_size=1, max_size=20, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'))),
        interaction_type=st.sampled_from(['like.created', 'comment.created', 'share.created'])
    )
    @settings(max_examples=100)
    async def test_interaction_invalidates_trending_cache(self, post_id, interaction_type):
        """
        Interactions should also invalidate trending cache since engagement changed.
        """
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.cache') as mock_cache:
            # Update score from interaction
            await service.update_score_from_interaction(post_id, interaction_type)
            
            # Verify: trending cache was invalidated
            trending_cache_key = "feed:trending"
            delete_calls = mock_cache.delete.call_args_list
            deleted_keys = [call[0][0] for call in delete_calls]
            
            assert trending_cache_key in deleted_keys, (
                f"Trending cache should be invalidated after {interaction_type}"
            )
    
    @given(
        post_ids=st.lists(
            st.text(min_size=1, max_size=20, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'))),
            min_size=1,
            max_size=10,
            unique=True
        ),
        interaction_type=st.sampled_from(['like.created', 'comment.created', 'share.created'])
    )
    @settings(max_examples=100)
    async def test_multiple_interactions_invalidate_multiple_scores(self, post_ids, interaction_type):
        """
        Multiple interactions on different posts should invalidate all their scores.
        """
        service = RecommendationService()
        
        with patch('src.services.recommendation_service.cache') as mock_cache:
            # Process interactions for all posts
            for post_id in post_ids:
                await service.update_score_from_interaction(post_id, interaction_type)
            
            # Verify: all score caches were invalidated
            delete_calls = mock_cache.delete.call_args_list
            deleted_keys = [call[0][0] for call in delete_calls]
            
            for post_id in post_ids:
                expected_key = f"score:{post_id}"
                assert expected_key in deleted_keys, (
                    f"Score cache for post {post_id} should be invalidated"
                )
    
    @given(
        post_id=st.text(min_size=1, max_size=20, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd')))
    )
    @settings(max_examples=100)
    async def test_all_interaction_types_invalidate_score(self, post_id):
        """
        All types of interactions should invalidate the score cache.
        """
        service = RecommendationService()
        interaction_types = ['like.created', 'comment.created', 'share.created']
        
        for interaction_type in interaction_types:
            with patch('src.services.recommendation_service.cache') as mock_cache:
                # Update score from interaction
                await service.update_score_from_interaction(post_id, interaction_type)
                
                # Verify: score cache was invalidated
                score_cache_key = f"score:{post_id}"
                delete_calls = mock_cache.delete.call_args_list
                deleted_keys = [call[0][0] for call in delete_calls]
                
                assert score_cache_key in deleted_keys, (
                    f"Score cache should be invalidated for {interaction_type}"
                )
    
    @given(
        post_id=st.text(min_size=1, max_size=20, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'))),
        num_interactions=st.integers(min_value=1, max_value=20)
    )
    @settings(max_examples=100)
    async def test_repeated_interactions_always_invalidate(self, post_id, num_interactions):
        """
        Repeated interactions on the same post should always invalidate cache.
        """
        service = RecommendationService()
        interaction_type = 'like.created'
        
        with patch('src.services.recommendation_service.cache') as mock_cache:
            # Process multiple interactions on same post
            for _ in range(num_interactions):
                await service.update_score_from_interaction(post_id, interaction_type)
            
            # Verify: cache.delete was called num_interactions times for score
            # (plus num_interactions times for trending)
            assert mock_cache.delete.call_count >= num_interactions, (
                f"cache.delete should be called at least {num_interactions} times"
            )
            
            # Verify: score cache key appears in delete calls
            delete_calls = mock_cache.delete.call_args_list
            deleted_keys = [call[0][0] for call in delete_calls]
            score_cache_key = f"score:{post_id}"
            
            score_delete_count = deleted_keys.count(score_cache_key)
            assert score_delete_count == num_interactions, (
                f"Score cache should be deleted {num_interactions} times, was deleted {score_delete_count} times"
            )
