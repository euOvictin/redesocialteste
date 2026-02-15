from elasticsearch import Elasticsearch
from src.config import settings
import logging

logger = logging.getLogger(__name__)


class ElasticsearchClient:
    """Elasticsearch client wrapper"""
    
    def __init__(self):
        self.client = None
    
    def connect(self):
        """Connect to Elasticsearch"""
        try:
            self.client = Elasticsearch(
                [settings.elasticsearch_url],
                request_timeout=30,
                max_retries=3,
                retry_on_timeout=True
            )
            
            # Test connection
            if self.client.ping():
                logger.info(f"Connected to Elasticsearch at {settings.elasticsearch_url}")
            else:
                logger.error("Failed to ping Elasticsearch")
                raise ConnectionError("Cannot connect to Elasticsearch")
                
        except Exception as e:
            logger.error(f"Error connecting to Elasticsearch: {e}")
            raise
    
    def close(self):
        """Close Elasticsearch connection"""
        if self.client:
            self.client.close()
            logger.info("Elasticsearch connection closed")
    
    def get_client(self) -> Elasticsearch:
        """Get Elasticsearch client instance"""
        if not self.client:
            self.connect()
        return self.client


# Global client instance
es_client = ElasticsearchClient()
