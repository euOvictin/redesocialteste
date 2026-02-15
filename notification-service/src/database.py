"""MongoDB database connection."""
import logging
from motor.motor_asyncio import AsyncIOMotorClient
from src.config import settings

logger = logging.getLogger(__name__)

_client: AsyncIOMotorClient | None = None
_db = None


async def connect_db():
    """Connect to MongoDB."""
    global _client, _db
    try:
        _client = AsyncIOMotorClient(settings.mongo_uri)
        _db = _client[settings.mongo_db_name]
        await _db.command("ping")
        await _create_indexes()
        logger.info("MongoDB connected successfully")
    except Exception as e:
        logger.error(f"MongoDB connection failed: {e}")
        raise


async def disconnect_db():
    """Disconnect from MongoDB."""
    global _client, _db
    if _client:
        _client.close()
        _client = None
        _db = None
        logger.info("MongoDB disconnected")


def get_db():
    """Get database instance."""
    if _db is None:
        raise RuntimeError("Database not connected. Call connect_db() first.")
    return _db


async def _create_indexes():
    """Create MongoDB indexes."""
    db = get_db()
    notifications = db.notifications
    await notifications.create_index("user_id")
    await notifications.create_index([("user_id", 1), ("created_at", -1)])
    await notifications.create_index("created_at")
    
    preferences = db.notification_preferences
    await preferences.create_index("user_id", unique=True)
