"""Notification models."""
from datetime import datetime
from typing import Optional, Literal
from pydantic import BaseModel, Field
from bson import ObjectId


class PyObjectId(str):
    """Custom type for MongoDB ObjectId."""
    @classmethod
    def __get_validators__(cls):
        yield cls.validate

    @classmethod
    def validate(cls, v):
        if isinstance(v, ObjectId):
            return str(v)
        if isinstance(v, str):
            return v
        raise ValueError("Invalid ObjectId")


NotificationType = Literal["like", "comment", "comment_aggregated", "follow"]


class NotificationPreference(BaseModel):
    """User notification preferences."""
    user_id: str
    likes_enabled: bool = True
    comments_enabled: bool = True
    follows_enabled: bool = True
    push_enabled: bool = True
    updated_at: Optional[datetime] = None


class NotificationCreate(BaseModel):
    """Create notification request."""
    user_id: str
    type: NotificationType
    title: str
    body: str
    actor_id: str
    target_id: Optional[str] = None  # post_id, etc
    metadata: Optional[dict] = None
    aggregated_count: int = 1


class Notification(BaseModel):
    """Notification document."""
    id: Optional[str] = None
    user_id: str
    type: NotificationType
    title: str
    body: str
    actor_id: str
    target_id: Optional[str] = None
    metadata: Optional[dict] = None
    is_read: bool = False
    read_at: Optional[datetime] = None
    aggregated_count: int = 1
    created_at: datetime = Field(default_factory=datetime.utcnow)
