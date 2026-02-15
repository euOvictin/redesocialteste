"""Search service with fuzzy matching support"""
import logging
from typing import Dict, Any, List, Optional
from elasticsearch import Elasticsearch
from datetime import datetime

from src.indices import POSTS_INDEX, USERS_INDEX, HASHTAGS_INDEX

logger = logging.getLogger(__name__)


class SearchService:
    """Service for searching content with fuzzy matching"""
    
    def __init__(self, es_client: Elasticsearch):
        self.es = es_client
    
    async def search(
        self,
        query: str,
        search_type: Optional[str] = None,
        page: int = 1,
        page_size: int = 20
    ) -> Dict[str, Any]:
        """
        Search for content with fuzzy matching
        
        Args:
            query: Search query (minimum 2 characters)
            search_type: Filter by type ('posts', 'users', 'hashtags', or None for all)
            page: Page number (1-indexed)
            page_size: Number of results per page
            
        Returns:
            Dictionary with search results and metadata
        """
        # Validate query length
        if len(query) < 2:
            raise ValueError("Query must be at least 2 characters")
        
        # Calculate pagination
        from_index = (page - 1) * page_size
        
        # Build search based on type
        if search_type == 'posts':
            results = await self._search_posts(query, from_index, page_size)
        elif search_type == 'users':
            results = await self._search_users(query, from_index, page_size)
        elif search_type == 'hashtags':
            results = await self._search_hashtags(query, from_index, page_size)
        elif search_type is None:
            # Search all types
            results = await self._search_all(query, from_index, page_size)
        else:
            raise ValueError(f"Invalid search type: {search_type}")
        
        return results
    
    async def _search_posts(
        self,
        query: str,
        from_index: int,
        size: int
    ) -> Dict[str, Any]:
        """Search posts with fuzzy matching"""
        try:
            # Build fuzzy query
            search_query = {
                "query": {
                    "bool": {
                        "should": [
                            # Exact match (highest priority)
                            {
                                "match": {
                                    "content": {
                                        "query": query,
                                        "boost": 3.0
                                    }
                                }
                            },
                            # Fuzzy match (tolerate 1-2 character typos)
                            {
                                "match": {
                                    "content": {
                                        "query": query,
                                        "fuzziness": "AUTO",
                                        "boost": 1.0
                                    }
                                }
                            },
                            # Hashtag match
                            {
                                "term": {
                                    "hashtags": {
                                        "value": query.lower().lstrip('#'),
                                        "boost": 2.0
                                    }
                                }
                            }
                        ],
                        "minimum_should_match": 1
                    }
                },
                "from": from_index,
                "size": size,
                "sort": [
                    {"_score": {"order": "desc"}},
                    {"created_at": {"order": "desc"}}
                ]
            }
            
            # Execute search with timeout
            response = self.es.search(
                index=POSTS_INDEX,
                body=search_query,
                request_timeout=0.5  # 500ms timeout
            )
            
            # Extract results
            hits = response['hits']['hits']
            total = response['hits']['total']['value']
            
            posts = [hit['_source'] for hit in hits]
            
            return {
                "type": "posts",
                "results": posts,
                "total": total,
                "page": (from_index // size) + 1,
                "page_size": size,
                "has_more": (from_index + size) < total
            }
            
        except Exception as e:
            logger.error(f"Error searching posts: {e}")
            raise
    
    async def _search_users(
        self,
        query: str,
        from_index: int,
        size: int
    ) -> Dict[str, Any]:
        """Search users with fuzzy matching"""
        try:
            # Build fuzzy query
            search_query = {
                "query": {
                    "bool": {
                        "should": [
                            # Name exact match
                            {
                                "match": {
                                    "name": {
                                        "query": query,
                                        "boost": 3.0
                                    }
                                }
                            },
                            # Name fuzzy match
                            {
                                "match": {
                                    "name": {
                                        "query": query,
                                        "fuzziness": "AUTO",
                                        "boost": 2.0
                                    }
                                }
                            },
                            # Bio match
                            {
                                "match": {
                                    "bio": {
                                        "query": query,
                                        "fuzziness": "AUTO",
                                        "boost": 1.0
                                    }
                                }
                            }
                        ],
                        "minimum_should_match": 1
                    }
                },
                "from": from_index,
                "size": size,
                "sort": [
                    {"_score": {"order": "desc"}},
                    {"followers_count": {"order": "desc"}}
                ]
            }
            
            # Execute search with timeout
            response = self.es.search(
                index=USERS_INDEX,
                body=search_query,
                request_timeout=0.5  # 500ms timeout
            )
            
            # Extract results
            hits = response['hits']['hits']
            total = response['hits']['total']['value']
            
            users = [hit['_source'] for hit in hits]
            
            return {
                "type": "users",
                "results": users,
                "total": total,
                "page": (from_index // size) + 1,
                "page_size": size,
                "has_more": (from_index + size) < total
            }
            
        except Exception as e:
            logger.error(f"Error searching users: {e}")
            raise
    
    async def _search_hashtags(
        self,
        query: str,
        from_index: int,
        size: int
    ) -> Dict[str, Any]:
        """Search hashtags with fuzzy matching"""
        try:
            # Remove # prefix if present
            clean_query = query.lower().lstrip('#')
            
            # Build fuzzy query
            search_query = {
                "query": {
                    "bool": {
                        "should": [
                            # Exact prefix match
                            {
                                "prefix": {
                                    "tag": {
                                        "value": clean_query,
                                        "boost": 3.0
                                    }
                                }
                            },
                            # Fuzzy match
                            {
                                "fuzzy": {
                                    "tag": {
                                        "value": clean_query,
                                        "fuzziness": "AUTO",
                                        "boost": 1.0
                                    }
                                }
                            }
                        ],
                        "minimum_should_match": 1
                    }
                },
                "from": from_index,
                "size": size,
                "sort": [
                    {"_score": {"order": "desc"}},
                    {"posts_count": {"order": "desc"}},
                    {"last_used": {"order": "desc"}}
                ]
            }
            
            # Execute search with timeout
            response = self.es.search(
                index=HASHTAGS_INDEX,
                body=search_query,
                request_timeout=0.5  # 500ms timeout
            )
            
            # Extract results
            hits = response['hits']['hits']
            total = response['hits']['total']['value']
            
            hashtags = [hit['_source'] for hit in hits]
            
            return {
                "type": "hashtags",
                "results": hashtags,
                "total": total,
                "page": (from_index // size) + 1,
                "page_size": size,
                "has_more": (from_index + size) < total
            }
            
        except Exception as e:
            logger.error(f"Error searching hashtags: {e}")
            raise
    
    async def _search_all(
        self,
        query: str,
        from_index: int,
        size: int
    ) -> Dict[str, Any]:
        """Search across all types"""
        try:
            # Search each type with smaller page size
            type_size = size // 3 + 1
            
            posts_result = await self._search_posts(query, 0, type_size)
            users_result = await self._search_users(query, 0, type_size)
            hashtags_result = await self._search_hashtags(query, 0, type_size)
            
            return {
                "type": "all",
                "results": {
                    "posts": posts_result["results"],
                    "users": users_result["results"],
                    "hashtags": hashtags_result["results"]
                },
                "total": {
                    "posts": posts_result["total"],
                    "users": users_result["total"],
                    "hashtags": hashtags_result["total"]
                },
                "page": 1,
                "page_size": size
            }
            
        except Exception as e:
            logger.error(f"Error searching all types: {e}")
            raise
