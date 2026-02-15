"""Unit tests for search service"""
import pytest
from unittest.mock import Mock, AsyncMock
from src.search_service import SearchService


@pytest.fixture
def mock_es_client():
    """Mock Elasticsearch client"""
    return Mock()


@pytest.fixture
def search_service(mock_es_client):
    """Create search service with mock client"""
    return SearchService(mock_es_client)


class TestSearchService:
    """Test search service functionality"""
    
    @pytest.mark.asyncio
    async def test_search_query_too_short(self, search_service):
        """Test that queries shorter than 2 characters are rejected"""
        with pytest.raises(ValueError, match="Query must be at least 2 characters"):
            await search_service.search("a")
    
    @pytest.mark.asyncio
    async def test_search_invalid_type(self, search_service):
        """Test that invalid search types are rejected"""
        with pytest.raises(ValueError, match="Invalid search type"):
            await search_service.search("test", search_type="invalid")
    
    @pytest.mark.asyncio
    async def test_search_posts_returns_results(self, search_service, mock_es_client):
        """Test searching posts returns formatted results"""
        # Mock Elasticsearch response
        mock_es_client.search.return_value = {
            'hits': {
                'hits': [
                    {
                        '_source': {
                            'id': 'post1',
                            'content': 'Test post',
                            'user_id': 'user1'
                        }
                    }
                ],
                'total': {'value': 1}
            }
        }
        
        result = await search_service.search("test", search_type="posts")
        
        assert result['type'] == 'posts'
        assert len(result['results']) == 1
        assert result['results'][0]['id'] == 'post1'
        assert result['total'] == 1
        assert result['page'] == 1
    
    @pytest.mark.asyncio
    async def test_search_users_returns_results(self, search_service, mock_es_client):
        """Test searching users returns formatted results"""
        # Mock Elasticsearch response
        mock_es_client.search.return_value = {
            'hits': {
                'hits': [
                    {
                        '_source': {
                            'id': 'user1',
                            'name': 'Test User',
                            'email': 'test@example.com'
                        }
                    }
                ],
                'total': {'value': 1}
            }
        }
        
        result = await search_service.search("test", search_type="users")
        
        assert result['type'] == 'users'
        assert len(result['results']) == 1
        assert result['results'][0]['id'] == 'user1'
        assert result['total'] == 1
    
    @pytest.mark.asyncio
    async def test_search_hashtags_returns_results(self, search_service, mock_es_client):
        """Test searching hashtags returns formatted results"""
        # Mock Elasticsearch response
        mock_es_client.search.return_value = {
            'hits': {
                'hits': [
                    {
                        '_source': {
                            'tag': 'test',
                            'posts_count': 10,
                            'trending': False
                        }
                    }
                ],
                'total': {'value': 1}
            }
        }
        
        result = await search_service.search("#test", search_type="hashtags")
        
        assert result['type'] == 'hashtags'
        assert len(result['results']) == 1
        assert result['results'][0]['tag'] == 'test'
        assert result['total'] == 1
    
    @pytest.mark.asyncio
    async def test_search_pagination(self, search_service, mock_es_client):
        """Test pagination parameters are correctly applied"""
        # Mock Elasticsearch response
        mock_es_client.search.return_value = {
            'hits': {
                'hits': [],
                'total': {'value': 100}
            }
        }
        
        result = await search_service.search("test", search_type="posts", page=2, page_size=10)
        
        # Verify pagination in result
        assert result['page'] == 2
        assert result['page_size'] == 10
        assert result['has_more'] is True
        
        # Verify Elasticsearch was called with correct from/size
        call_args = mock_es_client.search.call_args
        assert call_args[1]['body']['from'] == 10  # (page 2 - 1) * 10
        assert call_args[1]['body']['size'] == 10
    
    @pytest.mark.asyncio
    async def test_search_timeout_configured(self, search_service, mock_es_client):
        """Test that search has 500ms timeout configured"""
        # Mock Elasticsearch response
        mock_es_client.search.return_value = {
            'hits': {
                'hits': [],
                'total': {'value': 0}
            }
        }
        
        await search_service.search("test", search_type="posts")
        
        # Verify timeout is set to 0.5 seconds (500ms)
        call_args = mock_es_client.search.call_args
        assert call_args[1]['request_timeout'] == 0.5
    
    @pytest.mark.asyncio
    async def test_search_posts_fuzzy_query_structure(self, search_service, mock_es_client):
        """Test that fuzzy matching is configured in the query"""
        # Mock Elasticsearch response
        mock_es_client.search.return_value = {
            'hits': {
                'hits': [],
                'total': {'value': 0}
            }
        }
        
        await search_service.search("test", search_type="posts")
        
        # Verify fuzzy query structure
        call_args = mock_es_client.search.call_args
        query = call_args[1]['body']['query']
        
        # Check that bool query with should clauses exists
        assert 'bool' in query
        assert 'should' in query['bool']
        
        # Check for fuzzy matching
        should_clauses = query['bool']['should']
        fuzzy_clause = next((c for c in should_clauses if 'match' in c and 'fuzziness' in c['match']['content']), None)
        assert fuzzy_clause is not None
        assert fuzzy_clause['match']['content']['fuzziness'] == 'AUTO'
    
    @pytest.mark.asyncio
    async def test_search_hashtags_removes_hash_prefix(self, search_service, mock_es_client):
        """Test that # prefix is removed from hashtag queries"""
        # Mock Elasticsearch response
        mock_es_client.search.return_value = {
            'hits': {
                'hits': [],
                'total': {'value': 0}
            }
        }
        
        await search_service.search("#test", search_type="hashtags")
        
        # Verify query uses cleaned tag without #
        call_args = mock_es_client.search.call_args
        query = call_args[1]['body']['query']
        
        # Check prefix query
        should_clauses = query['bool']['should']
        prefix_clause = next((c for c in should_clauses if 'prefix' in c), None)
        assert prefix_clause is not None
        assert prefix_clause['prefix']['tag']['value'] == 'test'  # No # prefix
    
    @pytest.mark.asyncio
    async def test_search_all_types(self, search_service, mock_es_client):
        """Test searching all types returns combined results"""
        # Mock Elasticsearch response
        mock_es_client.search.return_value = {
            'hits': {
                'hits': [
                    {'_source': {'id': 'item1'}}
                ],
                'total': {'value': 1}
            }
        }
        
        result = await search_service.search("test", search_type=None)
        
        assert result['type'] == 'all'
        assert 'posts' in result['results']
        assert 'users' in result['results']
        assert 'hashtags' in result['results']
        assert 'posts' in result['total']
        assert 'users' in result['total']
        assert 'hashtags' in result['total']
    
    @pytest.mark.asyncio
    async def test_search_has_more_flag(self, search_service, mock_es_client):
        """Test has_more flag is correctly set"""
        # Test when there are more results
        mock_es_client.search.return_value = {
            'hits': {
                'hits': [{'_source': {'id': f'post{i}'}} for i in range(20)],
                'total': {'value': 50}
            }
        }
        
        result = await search_service.search("test", search_type="posts", page=1, page_size=20)
        assert result['has_more'] is True
        
        # Test when no more results
        mock_es_client.search.return_value = {
            'hits': {
                'hits': [{'_source': {'id': f'post{i}'}} for i in range(10)],
                'total': {'value': 10}
            }
        }
        
        result = await search_service.search("test", search_type="posts", page=1, page_size=20)
        assert result['has_more'] is False
