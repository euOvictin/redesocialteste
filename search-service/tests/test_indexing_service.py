"""Unit tests for indexing service"""
import pytest
from unittest.mock import Mock, MagicMock
from datetime import datetime

from src.indexing_service import IndexingService


@pytest.fixture
def mock_es_client():
    """Mock Elasticsearch client"""
    return Mock()


@pytest.fixture
def indexing_service(mock_es_client):
    """Create indexing service with mock client"""
    return IndexingService(mock_es_client)


class TestHashtagExtraction:
    """Test hashtag extraction functionality"""
    
    def test_extract_single_hashtag(self, indexing_service):
        """Test extracting a single hashtag"""
        content = "This is a post with #python"
        hashtags = indexing_service.extract_hashtags(content)
        assert hashtags == ['python']
    
    def test_extract_multiple_hashtags(self, indexing_service):
        """Test extracting multiple hashtags"""
        content = "Learning #python and #javascript today! #coding"
        hashtags = indexing_service.extract_hashtags(content)
        assert set(hashtags) == {'python', 'javascript', 'coding'}
    
    def test_extract_hashtags_with_numbers(self, indexing_service):
        """Test extracting hashtags with numbers"""
        content = "Check out #python3 and #web2.0"
        hashtags = indexing_service.extract_hashtags(content)
        assert 'python3' in hashtags
    
    def test_extract_hashtags_case_insensitive(self, indexing_service):
        """Test hashtags are converted to lowercase"""
        content = "#Python #PYTHON #python"
        hashtags = indexing_service.extract_hashtags(content)
        assert hashtags == ['python']  # Should be deduplicated
    
    def test_extract_hashtags_empty_content(self, indexing_service):
        """Test extracting hashtags from empty content"""
        assert indexing_service.extract_hashtags("") == []
        assert indexing_service.extract_hashtags(None) == []
    
    def test_extract_hashtags_no_hashtags(self, indexing_service):
        """Test content without hashtags"""
        content = "This is a post without any tags"
        hashtags = indexing_service.extract_hashtags(content)
        assert hashtags == []
    
    def test_extract_hashtags_with_underscores(self, indexing_service):
        """Test hashtags with underscores"""
        content = "Using #machine_learning and #deep_learning"
        hashtags = indexing_service.extract_hashtags(content)
        assert set(hashtags) == {'machine_learning', 'deep_learning'}


class TestPostIndexing:
    """Test post indexing functionality"""
    
    @pytest.mark.asyncio
    async def test_index_post_success(self, indexing_service, mock_es_client):
        """Test successful post indexing"""
        post_data = {
            'id': 'post-123',
            'user_id': 'user-456',
            'content': 'Test post with #hashtag',
            'media_urls': [],
            'likes_count': 0,
            'comments_count': 0,
            'shares_count': 0,
            'created_at': '2024-01-01T00:00:00Z'
        }
        
        mock_es_client.index = Mock()
        result = await indexing_service.index_post(post_data)
        
        assert result is True
        assert mock_es_client.index.called
    
    @pytest.mark.asyncio
    async def test_index_post_missing_id(self, indexing_service, mock_es_client):
        """Test indexing post without ID fails"""
        post_data = {
            'user_id': 'user-456',
            'content': 'Test post'
        }
        
        result = await indexing_service.index_post(post_data)
        assert result is False
    
    @pytest.mark.asyncio
    async def test_index_post_extracts_hashtags(self, indexing_service, mock_es_client):
        """Test that post indexing extracts hashtags"""
        post_data = {
            'id': 'post-123',
            'user_id': 'user-456',
            'content': 'Post with #python and #coding',
            'created_at': '2024-01-01T00:00:00Z'
        }
        
        mock_es_client.index = Mock()
        mock_es_client.get = Mock(side_effect=Exception("Not found"))
        
        result = await indexing_service.index_post(post_data)
        
        assert result is True
        # Verify the document was indexed with hashtags
        call_args = mock_es_client.index.call_args_list[0]
        doc = call_args[1]['document']
        assert set(doc['hashtags']) == {'python', 'coding'}
    
    @pytest.mark.asyncio
    async def test_index_post_with_defaults(self, indexing_service, mock_es_client):
        """Test indexing post with minimal data uses defaults"""
        post_data = {
            'id': 'post-123',
            'user_id': 'user-456',
            'content': 'Minimal post'
        }
        
        mock_es_client.index = Mock()
        result = await indexing_service.index_post(post_data)
        
        assert result is True
        call_args = mock_es_client.index.call_args_list[0]
        doc = call_args[1]['document']
        assert doc['likes_count'] == 0
        assert doc['comments_count'] == 0
        assert doc['shares_count'] == 0


class TestUserIndexing:
    """Test user indexing functionality"""
    
    @pytest.mark.asyncio
    async def test_index_user_success(self, indexing_service, mock_es_client):
        """Test successful user indexing"""
        user_data = {
            'id': 'user-123',
            'email': 'test@example.com',
            'name': 'Test User',
            'bio': 'Test bio',
            'followers_count': 10,
            'following_count': 5,
            'created_at': '2024-01-01T00:00:00Z'
        }
        
        mock_es_client.index = Mock()
        result = await indexing_service.index_user(user_data)
        
        assert result is True
        assert mock_es_client.index.called
    
    @pytest.mark.asyncio
    async def test_index_user_missing_id(self, indexing_service, mock_es_client):
        """Test indexing user without ID fails"""
        user_data = {
            'email': 'test@example.com',
            'name': 'Test User'
        }
        
        result = await indexing_service.index_user(user_data)
        assert result is False
    
    @pytest.mark.asyncio
    async def test_index_user_with_defaults(self, indexing_service, mock_es_client):
        """Test indexing user with minimal data uses defaults"""
        user_data = {
            'id': 'user-123',
            'email': 'test@example.com',
            'name': 'Test User'
        }
        
        mock_es_client.index = Mock()
        result = await indexing_service.index_user(user_data)
        
        assert result is True
        call_args = mock_es_client.index.call_args_list[0]
        doc = call_args[1]['document']
        assert doc['bio'] == ''
        assert doc['followers_count'] == 0
        assert doc['following_count'] == 0


class TestHashtagIndexing:
    """Test hashtag indexing functionality"""
    
    @pytest.mark.asyncio
    async def test_index_new_hashtag(self, indexing_service, mock_es_client):
        """Test indexing a new hashtag"""
        hashtags = ['python']
        
        mock_es_client.get = Mock(side_effect=Exception("Not found"))
        mock_es_client.index = Mock()
        
        result = await indexing_service.index_hashtags(hashtags)
        
        assert result is True
        assert mock_es_client.index.called
        call_args = mock_es_client.index.call_args_list[0]
        doc = call_args[1]['document']
        assert doc['tag'] == 'python'
        assert doc['posts_count'] == 1
    
    @pytest.mark.asyncio
    async def test_index_existing_hashtag(self, indexing_service, mock_es_client):
        """Test updating an existing hashtag"""
        hashtags = ['python']
        
        existing_doc = {
            '_source': {
                'tag': 'python',
                'posts_count': 5,
                'trending': False
            }
        }
        mock_es_client.get = Mock(return_value=existing_doc)
        mock_es_client.index = Mock()
        
        result = await indexing_service.index_hashtags(hashtags)
        
        assert result is True
        call_args = mock_es_client.index.call_args_list[0]
        doc = call_args[1]['document']
        assert doc['posts_count'] == 6  # Incremented
    
    @pytest.mark.asyncio
    async def test_index_multiple_hashtags(self, indexing_service, mock_es_client):
        """Test indexing multiple hashtags"""
        hashtags = ['python', 'javascript', 'coding']
        
        mock_es_client.get = Mock(side_effect=Exception("Not found"))
        mock_es_client.index = Mock()
        
        result = await indexing_service.index_hashtags(hashtags)
        
        assert result is True
        assert mock_es_client.index.call_count == 3

