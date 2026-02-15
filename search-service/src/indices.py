"""Elasticsearch index mappings and creation"""
import logging
from elasticsearch import Elasticsearch

logger = logging.getLogger(__name__)


# Index mappings
POSTS_INDEX = "posts"
USERS_INDEX = "users"
HASHTAGS_INDEX = "hashtags"


POSTS_MAPPING = {
    "mappings": {
        "properties": {
            "id": {"type": "keyword"},
            "user_id": {"type": "keyword"},
            "content": {
                "type": "text",
                "analyzer": "standard",
                "fields": {
                    "keyword": {"type": "keyword"}
                }
            },
            "hashtags": {
                "type": "keyword"
            },
            "media_urls": {
                "type": "object",
                "enabled": False
            },
            "likes_count": {"type": "integer"},
            "comments_count": {"type": "integer"},
            "shares_count": {"type": "integer"},
            "created_at": {"type": "date"},
            "updated_at": {"type": "date"}
        }
    },
    "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0,
        "analysis": {
            "analyzer": {
                "standard": {
                    "type": "standard"
                }
            }
        }
    }
}


USERS_MAPPING = {
    "mappings": {
        "properties": {
            "id": {"type": "keyword"},
            "email": {"type": "keyword"},
            "name": {
                "type": "text",
                "analyzer": "standard",
                "fields": {
                    "keyword": {"type": "keyword"}
                }
            },
            "bio": {
                "type": "text",
                "analyzer": "standard"
            },
            "profile_picture_url": {"type": "keyword"},
            "followers_count": {"type": "integer"},
            "following_count": {"type": "integer"},
            "created_at": {"type": "date"}
        }
    },
    "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0
    }
}


HASHTAGS_MAPPING = {
    "mappings": {
        "properties": {
            "tag": {"type": "keyword"},
            "posts_count": {"type": "integer"},
            "trending": {"type": "boolean"},
            "last_used": {"type": "date"}
        }
    },
    "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0
    }
}


def create_indices(es: Elasticsearch):
    """Create all required indices if they don't exist"""
    indices = [
        (POSTS_INDEX, POSTS_MAPPING),
        (USERS_INDEX, USERS_MAPPING),
        (HASHTAGS_INDEX, HASHTAGS_MAPPING)
    ]
    
    for index_name, mapping in indices:
        try:
            if not es.indices.exists(index=index_name):
                es.indices.create(index=index_name, body=mapping)
                logger.info(f"Created index: {index_name}")
            else:
                logger.info(f"Index already exists: {index_name}")
        except Exception as e:
            logger.error(f"Error creating index {index_name}: {e}")
            raise


def delete_indices(es: Elasticsearch):
    """Delete all indices (useful for testing)"""
    indices = [POSTS_INDEX, USERS_INDEX, HASHTAGS_INDEX]
    
    for index_name in indices:
        try:
            if es.indices.exists(index=index_name):
                es.indices.delete(index=index_name)
                logger.info(f"Deleted index: {index_name}")
        except Exception as e:
            logger.error(f"Error deleting index {index_name}: {e}")
