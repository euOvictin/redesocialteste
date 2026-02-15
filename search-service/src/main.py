"""Search Service - FastAPI application"""
from fastapi import FastAPI, Query, HTTPException
from contextlib import asynccontextmanager
from typing import Optional
import logging

from src.config import settings
from src.elasticsearch_client import es_client
from src.indices import create_indices
from src.kafka_consumer import kafka_consumer_service
from src.search_service import SearchService

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan context manager for startup and shutdown events"""
    # Startup
    logger.info("Starting Search Service...")
    try:
        # Connect to Elasticsearch
        es_client.connect()
        create_indices(es_client.get_client())
        
        # Start Kafka consumers
        await kafka_consumer_service.start()
        
        logger.info("Search Service started successfully")
    except Exception as e:
        logger.error(f"Failed to start Search Service: {e}")
        raise
    
    yield
    
    # Shutdown
    logger.info("Shutting down Search Service...")
    
    # Stop Kafka consumers
    await kafka_consumer_service.stop()
    
    # Close Elasticsearch connection
    es_client.close()
    
    logger.info("Search Service shut down successfully")


# Create FastAPI app
app = FastAPI(
    title="Search Service",
    description="Search and indexing service for Rede Social Brasileira",
    version="1.0.0",
    lifespan=lifespan
)


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    try:
        # Check Elasticsearch connection
        es = es_client.get_client()
        if es.ping():
            return {
                "status": "healthy",
                "service": "search-service",
                "elasticsearch": "connected",
                "kafka_consumers": "running" if kafka_consumer_service.running else "stopped"
            }
        else:
            return {
                "status": "unhealthy",
                "service": "search-service",
                "elasticsearch": "disconnected",
                "kafka_consumers": "running" if kafka_consumer_service.running else "stopped"
            }
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return {
            "status": "unhealthy",
            "service": "search-service",
            "error": str(e)
        }


@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "service": "search-service",
        "version": "1.0.0",
        "status": "running"
    }


@app.get("/search")
async def search(
    q: str = Query(..., min_length=2, description="Search query (minimum 2 characters)"),
    type: Optional[str] = Query(None, description="Filter by type: posts, users, or hashtags"),
    page: int = Query(1, ge=1, description="Page number (1-indexed)"),
    page_size: int = Query(20, ge=1, le=100, description="Results per page (max 100)")
):
    """
    Search endpoint with fuzzy matching
    
    - **q**: Search query (minimum 2 characters)
    - **type**: Optional filter by type (posts, users, hashtags)
    - **page**: Page number (default: 1)
    - **page_size**: Results per page (default: 20, max: 100)
    
    Returns results in less than 500ms with fuzzy matching to tolerate typos.
    """
    try:
        # Validate type parameter
        if type and type not in ['posts', 'users', 'hashtags']:
            raise HTTPException(
                status_code=400,
                detail={
                    "error": {
                        "code": "INVALID_TYPE",
                        "message": "Type must be one of: posts, users, hashtags"
                    }
                }
            )
        
        # Create search service
        search_service = SearchService(es_client.get_client())
        
        # Execute search
        results = await search_service.search(
            query=q,
            search_type=type,
            page=page,
            page_size=page_size
        )
        
        return results
        
    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail={
                "error": {
                    "code": "QUERY_TOO_SHORT",
                    "message": str(e)
                }
            }
        )
    except Exception as e:
        logger.error(f"Search error: {e}")
        raise HTTPException(
            status_code=500,
            detail={
                "error": {
                    "code": "SEARCH_ERROR",
                    "message": "An error occurred while searching"
                }
            }
        )
