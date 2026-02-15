"""Indexing service for Elasticsearch"""
import logging
import re
from typing import List, Dict, Any
from datetime import datetime, timezone
from elasticsearch import Elasticsearch

from src.indices import POSTS_INDEX, USERS_INDEX, HASHTAGS_INDEX

logger = logging.getLogger(__name__)


class IndexingService:
    """Service for indexing content in Elasticsearch"""
    
    def __init__(self, es_client: Elasticsearch):
        self.es = es_client
    
    def extract_hashtags(self, content: str) -> List[str]:
        """Extract hashtags from content using pattern #palavra"""
        if not content:
            return []
        
        # Match hashtags: # followed by word characters (letters, numbers, underscore)
        hashtag_pattern = r'#(\w+)'
        hashtags = re.findall(hashtag_pattern, content)
        
        # Return unique hashtags in lowercase
        return list(set(tag.lower() for tag in hashtags))
    
    async def index_post(self, post_data: Dict[str, Any]) -> bool:
        """Index a post in Elasticsearch"""
        try:
            post_id = post_data.get('id')
            if not post_id:
                logger.error("Post data missing 'id' field")
                return False
            
            # Extract hashtags from content
            content = post_data.get('content', '')
            hashtags = self.extract_hashtags(content)
            
            # Prepare document for indexing
            doc = {
                'id': post_id,
                'user_id': post_data.get('user_id'),
                'content': content,
                'hashtags': hashtags,
                'media_urls': post_data.get('media_urls', []),
                'likes_count': post_data.get('likes_count', 0),
                'comments_count': post_data.get('comments_count', 0),
                'shares_count': post_data.get('shares_count', 0),
                'created_at': post_data.get('created_at', datetime.now(timezone.utc).isoformat()),
                'updated_at': post_data.get('updated_at', datetime.now(timezone.utc).isoformat())
            }
            
            # Index the document
            self.es.index(
                index=POSTS_INDEX,
                id=post_id,
                document=doc,
                refresh=True  # Make immediately searchable
            )
            
            logger.info(f"Indexed post: {post_id} with {len(hashtags)} hashtags")
            
            # Index hashtags
            if hashtags:
                await self.index_hashtags(hashtags)
            
            return True
            
        except Exception as e:
            logger.error(f"Error indexing post: {e}")
            return False
    
    async def index_user(self, user_data: Dict[str, Any]) -> bool:
        """Index a user in Elasticsearch"""
        try:
            user_id = user_data.get('id')
            if not user_id:
                logger.error("User data missing 'id' field")
                return False
            
            # Prepare document for indexing
            doc = {
                'id': user_id,
                'email': user_data.get('email'),
                'name': user_data.get('name'),
                'bio': user_data.get('bio', ''),
                'profile_picture_url': user_data.get('profile_picture_url', ''),
                'followers_count': user_data.get('followers_count', 0),
                'following_count': user_data.get('following_count', 0),
                'created_at': user_data.get('created_at', datetime.now(timezone.utc).isoformat())
            }
            
            # Index the document
            self.es.index(
                index=USERS_INDEX,
                id=user_id,
                document=doc,
                refresh=True  # Make immediately searchable
            )
            
            logger.info(f"Indexed user: {user_id}")
            return True
            
        except Exception as e:
            logger.error(f"Error indexing user: {e}")
            return False
    
    async def index_hashtags(self, hashtags: List[str]) -> bool:
        """Index or update hashtags in Elasticsearch"""
        try:
            for tag in hashtags:
                # Check if hashtag already exists
                try:
                    result = self.es.get(index=HASHTAGS_INDEX, id=tag)
                    existing_doc = result['_source']
                    
                    # Update existing hashtag
                    doc = {
                        'tag': tag,
                        'posts_count': existing_doc.get('posts_count', 0) + 1,
                        'trending': existing_doc.get('trending', False),
                        'last_used': datetime.now(timezone.utc).isoformat()
                    }
                except:
                    # Create new hashtag
                    doc = {
                        'tag': tag,
                        'posts_count': 1,
                        'trending': False,
                        'last_used': datetime.now(timezone.utc).isoformat()
                    }
                
                # Index the hashtag
                self.es.index(
                    index=HASHTAGS_INDEX,
                    id=tag,
                    document=doc,
                    refresh=True
                )
            
            logger.info(f"Indexed {len(hashtags)} hashtags")
            return True
            
        except Exception as e:
            logger.error(f"Error indexing hashtags: {e}")
            return False

