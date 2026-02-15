from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime


class Post(BaseModel):
    """Post model."""
    id: str
    user_id: str
    content: str
    likes_count: int = 0
    comments_count: int = 0
    shares_count: int = 0
    created_at: datetime
    relevance_score: Optional[float] = None


class FeedItem(BaseModel):
    """Feed item with post and relevance score."""
    post_id: str
    user_id: str
    relevance_score: float
    timestamp: datetime


class FeedResponse(BaseModel):
    """Feed response with pagination."""
    posts: List[Post]
    cursor: Optional[str] = None
    has_more: bool = False


class RelevanceScoreRequest(BaseModel):
    """Request to calculate relevance score."""
    user_id: str
    post_id: str


class RelevanceScoreResponse(BaseModel):
    """Response with calculated relevance score."""
    post_id: str
    relevance_score: float


class InteractionEvent(BaseModel):
    """Interaction event from Kafka."""
    event_type: str  # like.created, comment.created, share.created
    post_id: str
    user_id: str
    timestamp: datetime


class PostCreatedEvent(BaseModel):
    """Post created event from Kafka."""
    event_type: str = "post.created"
    post_id: str
    user_id: str
    timestamp: datetime
