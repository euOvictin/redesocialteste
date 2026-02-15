"""Notification Service - FastAPI application."""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from src.config import settings
from src.database import connect_db, disconnect_db
from src.routes import router
from src.services.kafka_consumer import start_consumer_background

logging.basicConfig(
    level=settings.log_level.upper(),
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan context manager for startup and shutdown."""
    # Startup
    logger.info("Starting Notification Service...")
    await connect_db()
    try:
        start_consumer_background()
    except Exception as e:
        logger.warning(f"Kafka consumer could not start: {e}")
    yield
    # Shutdown
    logger.info("Shutting down Notification Service...")
    await disconnect_db()


app = FastAPI(
    title="Notification Service",
    description="Notification and push service for Rede Social Brasileira",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router, prefix="/api/v1")


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    try:
        from src.database import get_db
        db = get_db()
        await db.command("ping")
        return {
            "status": "healthy",
            "service": "notification-service",
            "mongodb": "connected"
        }
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return {
            "status": "unhealthy",
            "service": "notification-service",
            "error": str(e)
        }


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "service": "notification-service",
        "version": "1.0.0",
        "status": "running"
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "src.main:app",
        host=settings.host,
        port=settings.port,
        reload=True,
    )
