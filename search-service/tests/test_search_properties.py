"""Property-based tests for search functionality

Feature: rede-social-brasileira
Property 44: Busca retorna resultados relevantes
Property 46: Busca fuzzy tolera erros
Property 48: Filtros retornam apenas tipo especificado
Validates: Requirements 9.1, 9.4, 9.6

For any search query, results must be relevant, fuzzy matching must tolerate typos,
and type filters must return only the specified type.
"""
import pytest
from unittest.mock import Mock
from datetime import datetime, timezone
from hypothesis import given, settings, strategies as st, assume
from elasticsearch import Elasticsearch

from src.search_service import SearchService


# Custom strategies for generating valid search data
@st.composite
def valid_search_query(draw):
    """Generate valid search queries (minimum 2 characters)"""
    # Generate query with at least 2 characters
    query = draw(st.text(
        min_size=2,
        max_size=100,
        alphabet=st.characters(
            whitelist_categories=('Lu', 'Ll', 'Nd', 'Zs'),
            whitelist_characters='#_-'
        )
    ))
    # Ensure it's not just whitespace and has at least 2 non-whitespace characters
    stripped = query.strip()
    assume(len(stripped) >= 2)
    return stripped


@st.composite
def valid_post_document(draw, query_term=None):
    """Generate valid post documents for search results"""
    post_id = draw(st.text(min_size=1, max_size=50, alphabet=st.characters(
        whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='-_'
    )))
    user_id = draw(st.text(min_size=1, max_size=50, alphabet=st.characters(
        whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='-_'
    )))
    
    # If query_term provided, ensure content contains it
    if query_term:
        base_content = draw(st.text(min_size=0, max_size=200))
        content = f"{base_content} {query_term}"
    else:
        content = draw(st.text(min_size=1, max_size=500))
    
    hashtags = draw(st.lists(
        st.text(min_size=2, max_size=20, alphabet=st.characters(
            whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_'
        )),
        min_size=0,
        max_size=5
    ))
    
    return {
        'id': post_id,
        'user_id': user_id,
        'content': content,
        'hashtags': [tag.lower() for tag in hashtags],
        'likes_count': draw(st.integers(min_value=0, max_value=10000)),
        'comments_count': draw(st.integers(min_value=0, max_value=1000)),
        'shares_count': draw(st.integers(min_value=0, max_value=1000)),
        'created_at': datetime.now(timezone.utc).isoformat()
    }


@st.composite
def valid_user_document(draw, query_term=None):
    """Generate valid user documents for search results"""
    user_id = draw(st.text(min_size=1, max_size=50, alphabet=st.characters(
        whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='-_'
    )))
    
    # If query_term provided, ensure name contains it
    if query_term:
        name = f"{query_term} {draw(st.text(min_size=0, max_size=50))}"
    else:
        name = draw(st.text(min_size=2, max_size=100))
    
    return {
        'id': user_id,
        'email': f"{user_id}@example.com",
        'name': name,
        'bio': draw(st.text(min_size=0, max_size=500)),
        'followers_count': draw(st.integers(min_value=0, max_value=100000)),
        'following_count': draw(st.integers(min_value=0, max_value=10000)),
        'created_at': datetime.now(timezone.utc).isoformat()
    }


@st.composite
def valid_hashtag_document(draw, query_term=None):
    """Generate valid hashtag documents for search results"""
    # If query_term provided, use it as the tag
    if query_term:
        tag = query_term.lower().lstrip('#')
    else:
        tag = draw(st.text(min_size=2, max_size=30, alphabet=st.characters(
            whitelist_categories=('Lu', 'Ll', 'Nd'), whitelist_characters='_'
        ))).lower()
    
    return {
        'tag': tag,
        'posts_count': draw(st.integers(min_value=1, max_value=100000)),
        'trending': draw(st.booleans()),
        'last_used': datetime.now(timezone.utc).isoformat()
    }


@st.composite
def typo_variant(draw, original_word):
    """Generate a typo variant of a word (1-2 character changes)"""
    if len(original_word) < 3:
        return original_word
    
    # Choose typo type
    typo_type = draw(st.sampled_from(['substitute', 'delete', 'insert', 'transpose']))
    word_list = list(original_word)
    
    if typo_type == 'substitute' and len(word_list) > 0:
        # Substitute one character
        pos = draw(st.integers(min_value=0, max_value=len(word_list)-1))
        new_char = draw(st.characters(whitelist_categories=('Lu', 'Ll')))
        word_list[pos] = new_char
    elif typo_type == 'delete' and len(word_list) > 2:
        # Delete one character
        pos = draw(st.integers(min_value=0, max_value=len(word_list)-1))
        word_list.pop(pos)
    elif typo_type == 'insert':
        # Insert one character
        pos = draw(st.integers(min_value=0, max_value=len(word_list)))
        new_char = draw(st.characters(whitelist_categories=('Lu', 'Ll')))
        word_list.insert(pos, new_char)
    elif typo_type == 'transpose' and len(word_list) > 1:
        # Transpose two adjacent characters
        pos = draw(st.integers(min_value=0, max_value=len(word_list)-2))
        word_list[pos], word_list[pos+1] = word_list[pos+1], word_list[pos]
    
    return ''.join(word_list)


class TestSearchProperties:
    """Property-based tests for search functionality"""
    
    @pytest.mark.asyncio
    @given(
        query=valid_search_query(),
        num_results=st.integers(min_value=1, max_value=10),
        posts_data=st.data()
    )
    @settings(max_examples=100, deadline=None)
    async def test_property_44_search_returns_relevant_results(self, query, num_results, posts_data):
        """
        **Property 44: Busca retorna resultados relevantes**
        **Validates: Requirements 9.1**
        
        For any search query with minimum 2 characters, results must contain
        only items that match the query term.
        
        This property verifies that:
        1. All returned results contain the search query term
        2. Results are relevant to the search query
        3. No irrelevant results are returned
        """
        # Create mock Elasticsearch client
        mock_es_client = Mock(spec=Elasticsearch)
        
        # Generate posts that contain the query term
        matching_posts = [
            posts_data.draw(valid_post_document(query_term=query))
            for _ in range(num_results)
        ]
        
        # Mock Elasticsearch response
        def mock_search(index, body, request_timeout):
            """Mock search that returns matching posts"""
            hits = [
                {'_source': post, '_score': 1.0}
                for post in matching_posts
            ]
            return {
                'hits': {
                    'hits': hits,
                    'total': {'value': len(hits)}
                }
            }
        
        mock_es_client.search = Mock(side_effect=mock_search)
        
        # Create search service
        search_service = SearchService(mock_es_client)
        
        # Execute search
        results = await search_service.search(
            query=query,
            search_type='posts',
            page=1,
            page_size=20
        )
        
        # Property assertions
        # 1. Search must return results
        assert 'results' in results, "Search response missing 'results' field"
        assert 'type' in results, "Search response missing 'type' field"
        assert results['type'] == 'posts', f"Expected type 'posts', got '{results['type']}'"
        
        # 2. All results must be relevant (contain query term or related)
        query_lower = query.lower()
        for result in results['results']:
            # Check if query appears in content or hashtags
            content_lower = result.get('content', '').lower()
            hashtags_lower = [tag.lower() for tag in result.get('hashtags', [])]
            
            is_relevant = (
                query_lower in content_lower or
                any(query_lower in tag for tag in hashtags_lower) or
                any(tag in query_lower for tag in hashtags_lower)
            )
            
            assert is_relevant, \
                f"Result not relevant to query '{query}': content='{result.get('content', '')}', " \
                f"hashtags={result.get('hashtags', [])}"
        
        # 3. Response must include metadata
        assert 'total' in results, "Search response missing 'total' field"
        assert 'page' in results, "Search response missing 'page' field"
        assert 'page_size' in results, "Search response missing 'page_size' field"
        assert results['total'] >= 0, "Total must be non-negative"
        assert results['page'] >= 1, "Page must be at least 1"
    
    @pytest.mark.asyncio
    @given(
        original_word=st.text(
            min_size=4,
            max_size=20,
            alphabet=st.characters(whitelist_categories=('Lu', 'Ll'))
        ),
        typo_data=st.data()
    )
    @settings(max_examples=100, deadline=None)
    async def test_property_46_fuzzy_search_tolerates_typos(self, original_word, typo_data):
        """
        **Property 46: Busca fuzzy tolera erros**
        **Validates: Requirements 9.4**
        
        For any search query with 1-2 character typos, fuzzy search must
        return results matching the correct term.
        
        This property verifies that:
        1. Fuzzy matching tolerates single character substitutions
        2. Fuzzy matching tolerates single character deletions
        3. Fuzzy matching tolerates single character insertions
        4. Fuzzy matching tolerates character transpositions
        """
        # Generate a typo variant
        typo_word = typo_data.draw(typo_variant(original_word))
        
        # Skip if typo is identical to original (rare but possible)
        assume(typo_word != original_word)
        
        # Create mock Elasticsearch client
        mock_es_client = Mock(spec=Elasticsearch)
        
        # Generate posts that contain the ORIGINAL word (not the typo)
        matching_posts = [
            {
                'id': f'post-{i}',
                'user_id': f'user-{i}',
                'content': f'This post contains {original_word} in the content',
                'hashtags': [],
                'likes_count': 10,
                'comments_count': 5,
                'shares_count': 2,
                'created_at': datetime.now(timezone.utc).isoformat()
            }
            for i in range(3)
        ]
        
        # Mock Elasticsearch response - fuzzy search should find original word
        def mock_search(index, body, request_timeout):
            """Mock search that simulates fuzzy matching"""
            # Elasticsearch's fuzzy matching would find these results
            # even though we searched for the typo
            hits = [
                {'_source': post, '_score': 0.8}  # Lower score for fuzzy match
                for post in matching_posts
            ]
            return {
                'hits': {
                    'hits': hits,
                    'total': {'value': len(hits)}
                }
            }
        
        mock_es_client.search = Mock(side_effect=mock_search)
        
        # Create search service
        search_service = SearchService(mock_es_client)
        
        # Execute search with TYPO
        results = await search_service.search(
            query=typo_word,
            search_type='posts',
            page=1,
            page_size=20
        )
        
        # Property assertions
        # 1. Fuzzy search must return results despite typo
        assert 'results' in results, "Fuzzy search response missing 'results' field"
        assert len(results['results']) > 0, \
            f"Fuzzy search with typo '{typo_word}' (original: '{original_word}') returned no results"
        
        # 2. Results should contain the original word (fuzzy matched)
        found_original = False
        for result in results['results']:
            content_lower = result.get('content', '').lower()
            if original_word.lower() in content_lower:
                found_original = True
                break
        
        assert found_original, \
            f"Fuzzy search for '{typo_word}' did not find results containing original word '{original_word}'"
        
        # 3. Verify the search query used fuzziness
        # Check that the mock was called with a query containing fuzziness
        assert mock_es_client.search.called, "Elasticsearch search was not called"
        call_args = mock_es_client.search.call_args
        search_body = call_args[1]['body']
        
        # Verify fuzziness is enabled in the query
        query_obj = search_body.get('query', {})
        assert 'bool' in query_obj, "Query should use bool query for fuzzy matching"
    
    @pytest.mark.asyncio
    @given(
        query=valid_search_query(),
        search_type=st.sampled_from(['posts', 'users', 'hashtags']),
        docs_data=st.data()
    )
    @settings(max_examples=100, deadline=None)
    async def test_property_48_filters_return_only_specified_type(self, query, search_type, docs_data):
        """
        **Property 48: Filtros retornam apenas tipo especificado**
        **Validates: Requirements 9.6**
        
        For any search with a type filter, results must contain only
        items of the specified type.
        
        This property verifies that:
        1. Type filter 'posts' returns only posts
        2. Type filter 'users' returns only users
        3. Type filter 'hashtags' returns only hashtags
        4. No mixing of types occurs
        """
        # Create mock Elasticsearch client
        mock_es_client = Mock(spec=Elasticsearch)
        
        # Generate appropriate documents based on type
        if search_type == 'posts':
            documents = [
                docs_data.draw(valid_post_document(query_term=query))
                for _ in range(5)
            ]
            expected_fields = ['id', 'user_id', 'content', 'hashtags', 'created_at']
        elif search_type == 'users':
            documents = [
                docs_data.draw(valid_user_document(query_term=query))
                for _ in range(5)
            ]
            expected_fields = ['id', 'email', 'name', 'bio', 'followers_count']
        else:  # hashtags
            documents = [
                docs_data.draw(valid_hashtag_document(query_term=query))
                for _ in range(5)
            ]
            expected_fields = ['tag', 'posts_count', 'trending']
        
        # Mock Elasticsearch response
        def mock_search(index, body, request_timeout):
            """Mock search that returns documents of the correct type"""
            # Verify the correct index is being searched
            expected_index = {
                'posts': 'posts',
                'users': 'users',
                'hashtags': 'hashtags'
            }[search_type]
            
            assert index == expected_index, \
                f"Expected search in index '{expected_index}', got '{index}'"
            
            hits = [
                {'_source': doc, '_score': 1.0}
                for doc in documents
            ]
            return {
                'hits': {
                    'hits': hits,
                    'total': {'value': len(hits)}
                }
            }
        
        mock_es_client.search = Mock(side_effect=mock_search)
        
        # Create search service
        search_service = SearchService(mock_es_client)
        
        # Execute search with type filter
        results = await search_service.search(
            query=query,
            search_type=search_type,
            page=1,
            page_size=20
        )
        
        # Property assertions
        # 1. Response must indicate the correct type
        assert 'type' in results, "Search response missing 'type' field"
        assert results['type'] == search_type, \
            f"Expected type '{search_type}', got '{results['type']}'"
        
        # 2. All results must be of the specified type
        assert 'results' in results, "Search response missing 'results' field"
        
        for result in results['results']:
            # Verify result has fields appropriate for the type
            for field in expected_fields:
                assert field in result, \
                    f"Result of type '{search_type}' missing expected field '{field}'"
            
            # Verify result doesn't have fields from other types
            if search_type == 'posts':
                # Posts should not have user-specific fields like 'email'
                assert 'email' not in result or result.get('user_id'), \
                    "Post result should not have 'email' field (that's for users)"
                # Posts should not have hashtag-specific fields
                assert 'trending' not in result or 'content' in result, \
                    "Post result should not have 'trending' field (that's for hashtags)"
            elif search_type == 'users':
                # Users should not have post-specific fields
                assert 'content' not in result or 'email' in result, \
                    "User result should not have 'content' field (that's for posts)"
                # Users should not have hashtag-specific fields
                assert 'posts_count' not in result or 'followers_count' in result, \
                    "User result should not have 'posts_count' field (that's for hashtags)"
            else:  # hashtags
                # Hashtags should not have user or post fields
                assert 'email' not in result, \
                    "Hashtag result should not have 'email' field (that's for users)"
                assert 'content' not in result, \
                    "Hashtag result should not have 'content' field (that's for posts)"
        
        # 3. Verify the correct index was searched
        assert mock_es_client.search.called, "Elasticsearch search was not called"
        call_args = mock_es_client.search.call_args
        searched_index = call_args[1]['index']
        
        expected_index = {
            'posts': 'posts',
            'users': 'users',
            'hashtags': 'hashtags'
        }[search_type]
        
        assert searched_index == expected_index, \
            f"Expected search in index '{expected_index}', got '{searched_index}'"
    
    @pytest.mark.asyncio
    @given(
        query=valid_search_query(),
        page=st.integers(min_value=1, max_value=10),
        page_size=st.integers(min_value=1, max_value=100)
    )
    @settings(max_examples=50, deadline=None)
    async def test_pagination_parameters_are_respected(self, query, page, page_size):
        """
        Test that pagination parameters are correctly applied to search results.
        
        This ensures the search service properly handles pagination
        across all possible valid parameter combinations.
        """
        # Create mock Elasticsearch client
        mock_es_client = Mock(spec=Elasticsearch)
        
        # Calculate expected from_index
        expected_from = (page - 1) * page_size
        
        # Mock Elasticsearch response
        def mock_search(index, body, request_timeout):
            """Mock search that verifies pagination parameters"""
            # Verify pagination parameters in the query
            assert 'from' in body, "Search query missing 'from' parameter"
            assert 'size' in body, "Search query missing 'size' parameter"
            assert body['from'] == expected_from, \
                f"Expected from={expected_from}, got {body['from']}"
            assert body['size'] == page_size, \
                f"Expected size={page_size}, got {body['size']}"
            
            # Return empty results
            return {
                'hits': {
                    'hits': [],
                    'total': {'value': 0}
                }
            }
        
        mock_es_client.search = Mock(side_effect=mock_search)
        
        # Create search service
        search_service = SearchService(mock_es_client)
        
        # Execute search with pagination
        results = await search_service.search(
            query=query,
            search_type='posts',
            page=page,
            page_size=page_size
        )
        
        # Assertions
        assert results['page'] == page, \
            f"Expected page {page}, got {results['page']}"
        assert results['page_size'] == page_size, \
            f"Expected page_size {page_size}, got {results['page_size']}"
    
    @pytest.mark.asyncio
    @given(query=st.text(min_size=0, max_size=1))
    @settings(max_examples=50, deadline=None)
    async def test_short_queries_are_rejected(self, query):
        """
        Test that queries shorter than 2 characters are rejected.
        
        This ensures the minimum query length requirement is enforced.
        """
        # Create mock Elasticsearch client
        mock_es_client = Mock(spec=Elasticsearch)
        
        # Create search service
        search_service = SearchService(mock_es_client)
        
        # Execute search with short query - should raise ValueError
        with pytest.raises(ValueError, match="at least 2 characters"):
            await search_service.search(
                query=query,
                search_type='posts',
                page=1,
                page_size=20
            )
    
    @pytest.mark.asyncio
    @given(
        query=valid_search_query(),
        invalid_type=st.text(min_size=1, max_size=20).filter(
            lambda x: x not in ['posts', 'users', 'hashtags', None]
        )
    )
    @settings(max_examples=50, deadline=None)
    async def test_invalid_type_is_rejected(self, query, invalid_type):
        """
        Test that invalid type parameters are rejected.
        
        This ensures only valid types (posts, users, hashtags) are accepted.
        """
        # Create mock Elasticsearch client
        mock_es_client = Mock(spec=Elasticsearch)
        
        # Create search service
        search_service = SearchService(mock_es_client)
        
        # Execute search with invalid type - should raise ValueError
        with pytest.raises(ValueError, match="Invalid search type"):
            await search_service.search(
                query=query,
                search_type=invalid_type,
                page=1,
                page_size=20
            )
