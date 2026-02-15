"""API routes for Notification Service."""
import logging
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Body
from pydantic import BaseModel
from typing import Optional

from src.auth import get_current_user_id
from src.services.notification_service import NotificationService

logger = logging.getLogger(__name__)

router = APIRouter(tags=["notifications"])
notification_service = NotificationService()


@router.get("/notifications")
async def list_notifications(
    user_id: str = Depends(get_current_user_id),
    page: int = Query(1, ge=1),
    limit: int = Query(50, ge=1, le=100),
    unread_only: bool = Query(False),
):
    """List notifications for current user with pagination."""
    notifications, total = await notification_service.list_notifications(
        user_id, page=page, limit=limit, unread_only=unread_only
    )
    return {
        "notifications": notifications,
        "pagination": {
            "page": page,
            "limit": limit,
            "total": total,
            "total_pages": (total + limit - 1) // limit,
        },
    }


@router.get("/notifications/{notification_id}")
async def get_notification(
    notification_id: str,
    user_id: str = Depends(get_current_user_id),
):
    """Get single notification and mark as read."""
    notifications, _ = await notification_service.list_notifications(user_id, limit=1000)
    for n in notifications:
        if n.get("id") == notification_id:
            await notification_service.mark_as_read(user_id, notification_id)
            return n
    raise HTTPException(status_code=404, detail="Notification not found")


@router.patch("/notifications/{notification_id}/read")
async def mark_notification_read(
    notification_id: str,
    user_id: str = Depends(get_current_user_id),
):
    """Mark notification as read."""
    success = await notification_service.mark_as_read(user_id, notification_id)
    if not success:
        raise HTTPException(status_code=404, detail="Notification not found")
    return {"success": True}


@router.delete("/notifications/{notification_id}")
async def delete_notification(
    notification_id: str,
    user_id: str = Depends(get_current_user_id),
):
    """Delete notification."""
    success = await notification_service.delete_notification(user_id, notification_id)
    if not success:
        raise HTTPException(status_code=404, detail="Notification not found")
    return {"success": True}


@router.get("/preferences")
async def get_preferences(
    user_id: str = Depends(get_current_user_id),
):
    """Get notification preferences."""
    return await notification_service.get_preferences(user_id)


class PreferencesUpdate(BaseModel):
    likes_enabled: Optional[bool] = None
    comments_enabled: Optional[bool] = None
    follows_enabled: Optional[bool] = None
    push_enabled: Optional[bool] = None


@router.put("/preferences")
async def update_preferences(
    prefs: PreferencesUpdate = Body(...),
    user_id: str = Depends(get_current_user_id),
):
    """Update notification preferences."""
    d = {k: v for k, v in (prefs.model_dump() if hasattr(prefs, 'model_dump') else prefs.dict()).items() if v is not None}
    return await notification_service.update_preferences(user_id, d)


class PushTokenRequest(BaseModel):
    token: str
    platform: str  # android | ios


@router.post("/preferences/push-token")
async def register_push_token(
    body: PushTokenRequest = Body(...),
    user_id: str = Depends(get_current_user_id),
):
    """Register FCM/APNs token for push notifications."""
    import datetime
    from src.database import get_db
    if body.platform not in ("android", "ios"):
        raise HTTPException(status_code=400, detail="platform must be android or ios")
    db = get_db()
    key = "fcm_token" if body.platform == "android" else "apns_token"
    await db.notification_preferences.update_one(
        {"user_id": user_id},
        {"$set": {key: body.token, "user_id": user_id, "updated_at": datetime.datetime.utcnow()}},
        upsert=True
    )
    return {"success": True}
