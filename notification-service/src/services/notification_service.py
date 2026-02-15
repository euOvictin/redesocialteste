"""Notification service - create and manage notifications."""
import logging
from datetime import datetime, timedelta
from typing import Optional

from src.database import get_db
from src.config import settings
from src.models import NotificationCreate, NotificationType

logger = logging.getLogger(__name__)


class NotificationService:
    """Service for managing notifications."""
    
    async def get_preferences(self, user_id: str) -> dict:
        """Get user notification preferences."""
        db = get_db()
        prefs = await db.notification_preferences.find_one({"user_id": user_id})
        if not prefs:
            return {
                "user_id": user_id,
                "likes_enabled": True,
                "comments_enabled": True,
                "follows_enabled": True,
                "push_enabled": True,
            }
        prefs.pop("_id", None)
        return prefs
    
    async def update_preferences(self, user_id: str, preferences: dict) -> dict:
        """Update user notification preferences."""
        db = get_db()
        prefs = {
            "user_id": user_id,
            "likes_enabled": preferences.get("likes_enabled", True),
            "comments_enabled": preferences.get("comments_enabled", True),
            "follows_enabled": preferences.get("follows_enabled", True),
            "push_enabled": preferences.get("push_enabled", True),
            "updated_at": datetime.utcnow(),
        }
        await db.notification_preferences.update_one(
            {"user_id": user_id},
            {"$set": prefs},
            upsert=True
        )
        return await self.get_preferences(user_id)
    
    async def should_create_notification(self, user_id: str, notif_type: str) -> bool:
        """Check if notification should be created based on preferences."""
        prefs = await self.get_preferences(user_id)
        type_map = {
            "like": "likes_enabled",
            "comment": "comments_enabled",
            "comment_aggregated": "comments_enabled",
            "follow": "follows_enabled",
        }
        key = type_map.get(notif_type, "likes_enabled")
        return prefs.get(key, True)
    
    async def create_notification(self, notif: NotificationCreate) -> Optional[str]:
        """Create a notification. Returns notification id or None if skipped."""
        if not await self.should_create_notification(notif.user_id, notif.type):
            logger.debug(f"Skipping notification for user {notif.user_id} - preference disabled")
            return None
        
        db = get_db()
        doc = {
            "user_id": notif.user_id,
            "type": notif.type,
            "title": notif.title,
            "body": notif.body,
            "actor_id": notif.actor_id,
            "target_id": notif.target_id,
            "metadata": notif.metadata or {},
            "is_read": False,
            "aggregated_count": notif.aggregated_count,
            "created_at": datetime.utcnow(),
        }
        result = await db.notifications.insert_one(doc)
        logger.info(f"Created notification {result.inserted_id} for user {notif.user_id}")
        return str(result.inserted_id)
    
    async def get_recent_aggregatable_comment(
        self, user_id: str, post_id: str
    ) -> Optional[dict]:
        """Get recent comment notification for same post within aggregation window."""
        db = get_db()
        window_start = datetime.utcnow() - timedelta(minutes=settings.comment_aggregation_minutes)
        return await db.notifications.find_one({
            "user_id": user_id,
            "type": {"$in": ["comment", "comment_aggregated"]},
            "target_id": post_id,
            "created_at": {"$gte": window_start},
        }, sort=[("created_at", -1)])
    
    async def aggregate_comment_notification(
        self, existing_id: str, additional_count: int = 1
    ) -> bool:
        """Update existing comment notification with aggregated count."""
        db = get_db()
        from bson import ObjectId
        result = await db.notifications.update_one(
            {"_id": ObjectId(existing_id)},
            {
                "$set": {"type": "comment_aggregated"},
                "$inc": {"aggregated_count": additional_count},
            }
        )
        return result.modified_count > 0
    
    async def list_notifications(
        self, user_id: str, page: int = 1, limit: int = 50, unread_only: bool = False
    ) -> tuple[list, int]:
        """List notifications for user with pagination."""
        db = get_db()
        query = {"user_id": user_id}
        if unread_only:
            query["is_read"] = False
        
        total = await db.notifications.count_documents(query)
        skip = (page - 1) * limit
        cursor = db.notifications.find(query).sort("created_at", -1).skip(skip).limit(limit)
        notifications = []
        async for doc in cursor:
            doc["id"] = str(doc.pop("_id"))
            notifications.append(doc)
        
        return notifications, total
    
    async def mark_as_read(self, user_id: str, notification_id: str) -> bool:
        """Mark notification as read."""
        db = get_db()
        from bson import ObjectId
        result = await db.notifications.update_one(
            {"_id": ObjectId(notification_id), "user_id": user_id},
            {"$set": {"is_read": True, "read_at": datetime.utcnow()}}
        )
        return result.modified_count > 0
    
    async def delete_notification(self, user_id: str, notification_id: str) -> bool:
        """Delete notification."""
        db = get_db()
        from bson import ObjectId
        result = await db.notifications.delete_one(
            {"_id": ObjectId(notification_id), "user_id": user_id}
        )
        return result.deleted_count > 0
    
    async def cleanup_old_notifications(self):
        """Remove notifications older than retention period."""
        db = get_db()
        cutoff = datetime.utcnow() - timedelta(days=settings.notification_retention_days)
        result = await db.notifications.delete_many({"created_at": {"$lt": cutoff}})
        logger.info(f"Cleaned up {result.deleted_count} old notifications")
        return result.deleted_count
