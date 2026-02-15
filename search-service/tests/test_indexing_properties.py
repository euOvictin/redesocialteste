"""Property-based tests for indexing service

Feature: rede-social-brasileira
Property 45: Novo post é indexado
Validates: Requirements 9.2, 9.3

For any post created, the content must be indexed and appear in subsequent searches.
"""
import pytest
from unittest.mock import Mock, AsyncMock
from datetime import datetime, timezone
from hypothesis import given, settings, strategies as st
from elasticsearch import Elasticsearch

from src.indexing_service import IndexingService


# Custom strategies for generating valid post data
@st.composite
def valid_post_data(draw):
    """Generate valid post data for property testing"""
    post_id = draw(st.text(min_size=1, max_size=50, alphabet=st.characters(
        whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='-_'
    )))
    user_id = draw(st.text(min_size=1, max_size=50, alphabet=st.characters(
        whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='-_'
    )))
    content = draw(st.text(min_size=1, max_size=5000))
    
    # Generate optional hashtags in content
    num_hashtags = draw(st.integers(min_value=0, max_value=5))
    if num_hashtags > 0:
        hashtags = [
            f"#{draw(st.text(min_size=2, max_size=20, alphabet=st.characters(whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_')))}"
            for _ in range(num_hashtags)
        ]
        content = content + " " + " ".join(hashtags)
    
    media_urls = draw(st.lists(
        st.text(min_size=10, max_size=200),
        min_size=0,
        max_size=10
    ))
    
    likes_count = draw(st.integers(min_value=0, max_value=1000000))
    comments_count = draw(st.integers(min_value=0, max_value=100000))
    shares_count = draw(st.integers(min_value=0, max_value=100000))
    
    return {
        'id': post_id,
        'user_id': user_id,
        'content': content,
        'media_urls': media_urls,
        'likes_count': likes_count,
        'comments_count': comments_count,
        'shares_count': shares_count,
        'created_at': datetime.now(timezone.utc).isoformat(),
        'updated_at': datetime.now(timezone.utc).isoformat()
    }


class TestIndexingProperties:
    """Property-based tests for indexing functionality"""
    
    @pytest.mark.asyncio
    @given(post_data=valid_post_data())
    @settings(max_examples=100, deadline=None)
    async def test_property_45_new_post_is_indexed(self, post_data):
        """
        **Property 45: Novo post é indexado**
        **Validates: Requirements 9.2, 9.3**
        
        For any post created, the content must be indexed and appear in subsequent searches.
        
        This property verifies that:
        1. Any valid post can be successfully indexed
        2. The indexed document contains all required fields
        3. The post becomes searchable after indexing
        """
        # Create mock Elasticsearch client
        mock_es_client = Mock(spec=Elasticsearch)
        indexed_documents = {}
        
        def mock_index(index, id, document, refresh=False):
            """Mock index function that stores documents"""
            indexed_documents[id] = {
                'index': index,
                'document': document,
                'refresh': refresh
            }
            return {'result': 'created', '_id': id}
        
        def mock_get(index, id):
            """Mock get function for hashtag lookups"""
            raise Exception("Not found")  # Simulate new hashtag
        
        mock_es_client.index = Mock(side_effect=mock_index)
        mock_es_client.get = Mock(side_effect=mock_get)
        
        # Create indexing service
        indexing_service = IndexingService(mock_es_client)
        
        # Index the post
        result = await indexing_service.index_post(post_data)
        
        # Property assertions
        # 1. Indexing must succeed for any valid post
        assert result is True, f"Failed to index post with id: {post_data['id']}"
        
        # 2. Post must be indexed in Elasticsearch
        assert post_data['id'] in indexed_documents, \
            f"Post {post_data['id']} was not found in indexed documents"
        
        indexed_post = indexed_documents[post_data['id']]
        
        # 3. Indexed document must be in the correct index
        assert indexed_post['index'] == 'posts', \
            f"Post indexed in wrong index: {indexed_post['index']}"
        
        # 4. Indexed document must contain all required fields
        doc = indexed_post['document']
        assert 'id' in doc, "Indexed document missing 'id' field"
        assert 'user_id' in doc, "Indexed document missing 'user_id' field"
        assert 'content' in doc, "Indexed document missing 'content' field"
        assert 'hashtags' in doc, "Indexed document missing 'hashtags' field"
        assert 'created_at' in doc, "Indexed document missing 'created_at' field"
        
        # 5. Indexed document must preserve original data
        assert doc['id'] == post_data['id'], \
            f"Post ID mismatch: expected {post_data['id']}, got {doc['id']}"
        assert doc['user_id'] == post_data['user_id'], \
            f"User ID mismatch: expected {post_data['user_id']}, got {doc['user_id']}"
        assert doc['content'] == post_data['content'], \
            "Content was modified during indexing"
        assert doc['likes_count'] == post_data['likes_count'], \
            "Likes count was modified during indexing"
        assert doc['comments_count'] == post_data['comments_count'], \
            "Comments count was modified during indexing"
        
        # 6. Post must be immediately searchable (refresh=True)
        assert indexed_post['refresh'] is True, \
            "Post was not indexed with refresh=True, won't be immediately searchable"
        
        # 7. Hashtags must be extracted from content
        content = post_data['content']
        if '#' in content:
            assert len(doc['hashtags']) > 0, \
                "Hashtags present in content but not extracted"
            # Verify all hashtags are lowercase
            for tag in doc['hashtags']:
                assert tag == tag.lower(), \
                    f"Hashtag '{tag}' is not lowercase"
    
    @pytest.mark.asyncio
    @given(
        post_id=st.text(min_size=1, max_size=50),
        user_id=st.text(min_size=1, max_size=50),
        content=st.text(min_size=1, max_size=5000)
    )
    @settings(max_examples=100, deadline=None)
    async def test_minimal_post_is_indexed(self, post_id, user_id, content):
        """
        Test that posts with minimal required fields can be indexed.
        
        This ensures the indexing service handles posts with only
        the essential fields (id, user_id, content).
        """
        # Create mock Elasticsearch client
        mock_es_client = Mock(spec=Elasticsearch)
        indexed_documents = {}
        
        def mock_index(index, id, document, refresh=False):
            indexed_documents[id] = document
            return {'result': 'created', '_id': id}
        
        def mock_get(index, id):
            raise Exception("Not found")
        
        mock_es_client.index = Mock(side_effect=mock_index)
        mock_es_client.get = Mock(side_effect=mock_get)
        
        # Create indexing service
        indexing_service = IndexingService(mock_es_client)
        
        # Create minimal post data
        minimal_post = {
            'id': post_id,
            'user_id': user_id,
            'content': content
        }
        
        # Index the post
        result = await indexing_service.index_post(minimal_post)
        
        # Assertions
        assert result is True, "Failed to index minimal post"
        assert post_id in indexed_documents, "Minimal post was not indexed"
        
        doc = indexed_documents[post_id]
        
        # Verify defaults are applied
        assert doc['likes_count'] == 0, "Default likes_count not applied"
        assert doc['comments_count'] == 0, "Default comments_count not applied"
        assert doc['shares_count'] == 0, "Default shares_count not applied"
        assert doc['media_urls'] == [], "Default media_urls not applied"
        assert 'created_at' in doc, "created_at not set"
        assert 'updated_at' in doc, "updated_at not set"
    
    @pytest.mark.asyncio
    @given(
        base_content=st.text(min_size=10, max_size=400),
        hashtag=st.text(min_size=2, max_size=20, alphabet=st.characters(
            whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_'
        ))
    )
    @settings(max_examples=100, deadline=None)
    async def test_hashtags_are_always_extracted(self, base_content, hashtag):
        """
        Test that hashtags are always extracted from content containing '#'.
        
        This property ensures hashtag extraction is reliable across
        all possible content variations.
        """
        # Create content with hashtag
        content = f"{base_content} #{hashtag}"
        
        # Create mock Elasticsearch client
        mock_es_client = Mock(spec=Elasticsearch)
        indexed_documents = {}
        
        def mock_index(index, id, document, refresh=False):
            indexed_documents[id] = document
            return {'result': 'created', '_id': id}
        
        def mock_get(index, id):
            raise Exception("Not found")
        
        mock_es_client.index = Mock(side_effect=mock_index)
        mock_es_client.get = Mock(side_effect=mock_get)
        
        # Create indexing service
        indexing_service = IndexingService(mock_es_client)
        
        # Create post with hashtags
        post_data = {
            'id': 'test-post',
            'user_id': 'test-user',
            'content': content
        }
        
        # Index the post
        result = await indexing_service.index_post(post_data)
        
        # Assertions
        assert result is True, "Failed to index post with hashtags"
        
        doc = indexed_documents['test-post']
        
        # Hashtags should be extracted
        assert len(doc['hashtags']) > 0, \
            f"Content contains hashtag but none were extracted: {content}"
        
        # The specific hashtag should be in the list (lowercase)
        assert hashtag.lower() in doc['hashtags'], \
            f"Hashtag '{hashtag}' not found in extracted hashtags: {doc['hashtags']}"
        
        # All extracted hashtags should be lowercase
        for tag in doc['hashtags']:
            assert tag == tag.lower(), \
                f"Hashtag '{tag}' is not lowercase"
    
    @pytest.mark.asyncio
    @given(
        num_posts=st.integers(min_value=1, max_value=10)
    )
    @settings(max_examples=20, deadline=None)
    async def test_multiple_posts_are_indexed_independently(self, num_posts):
        """
        Test that multiple posts can be indexed independently.
        
        This ensures the indexing service can handle multiple posts
        without interference between them.
        """
        # Create mock Elasticsearch client
        mock_es_client = Mock(spec=Elasticsearch)
        indexed_posts = {}
        indexed_hashtags = {}
        
        def mock_index(index, id, document, refresh=False):
            if index == 'posts':
                indexed_posts[id] = document
            elif index == 'hashtags':
                indexed_hashtags[id] = document
            return {'result': 'created', '_id': id}
        
        def mock_get(index, id):
            raise Exception("Not found")
        
        mock_es_client.index = Mock(side_effect=mock_index)
        mock_es_client.get = Mock(side_effect=mock_get)
        
        # Create indexing service
        indexing_service = IndexingService(mock_es_client)
        
        # Index multiple posts
        post_ids = []
        for i in range(num_posts):
            post_data = {
                'id': f'post-{i}',
                'user_id': f'user-{i}',
                'content': f'Test post {i}'
            }
            
            result = await indexing_service.index_post(post_data)
            assert result is True, f"Failed to index post {i}"
            post_ids.append(post_data['id'])
        
        # Verify all posts were indexed
        assert len(indexed_posts) == num_posts, \
            f"Expected {num_posts} posts indexed, got {len(indexed_posts)}"
        
        # Verify each post is independent
        for post_id in post_ids:
            assert post_id in indexed_posts, \
                f"Post {post_id} was not indexed"
