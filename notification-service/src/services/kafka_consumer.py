"""Kafka consumer for notification events."""
import asyncio
import json
import logging
from confluent_kafka import Consumer, KafkaError, KafkaException

from src.config import settings
from src.services.notification_service import NotificationService
from src.services.push_service import PushService
from src.models import NotificationCreate

logger = logging.getLogger(__name__)


class NotificationKafkaConsumer:
    """Consume events from Kafka and create notifications."""
    
    def __init__(self):
        self.consumer = None
        self.running = False
        self.notification_service = NotificationService()
        self.push_service = PushService()
    
    def _get_config(self):
        return {
            "bootstrap.servers": settings.kafka_bootstrap_servers,
            "group.id": settings.kafka_consumer_group,
            "auto.offset.reset": "earliest",
            "enable.auto.commit": True,
        }
    
    def start(self):
        """Start consuming (blocking - run in thread)."""
        try:
            self.consumer = Consumer(self._get_config())
            self.consumer.subscribe([settings.kafka_content_topic, settings.kafka_social_topic])
            self.running = True
            logger.info("Kafka consumer started for notifications")
            
            while self.running:
                msg = self.consumer.poll(timeout=1.0)
                if msg is None:
                    continue
                if msg.error():
                    if msg.error().code() == KafkaError._PARTITION_EOF:
                        continue
                    logger.error(f"Kafka error: {msg.error()}")
                    continue
                
                try:
                    asyncio.run(self._process_message(msg))
                except Exception as e:
                    logger.error(f"Error processing message: {e}", exc_info=True)
        except KafkaException as e:
            logger.error(f"Kafka exception: {e}")
        finally:
            if self.consumer:
                self.consumer.close()
    
    def stop(self):
        """Stop consuming."""
        self.running = False
    
    async def _process_message(self, msg):
        """Process a Kafka message."""
        try:
            data = json.loads(msg.value().decode("utf-8"))
            # Support both camelCase (Java) and snake_case
            event_type = data.get("event_type") or data.get("eventType")
            topic = msg.topic()
            
            if topic == settings.kafka_content_topic:
                await self._handle_content_event(event_type, data)
            elif topic == settings.kafka_social_topic:
                await self._handle_social_event(event_type, data)
        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse message: {e}")
    
    async def _handle_content_event(self, event_type: str, data: dict):
        """Handle content.events (like.created, comment.created)."""
        user_id = data.get("postAuthorId") or data.get("post_author_id")
        post_id = data.get("postId") or data.get("post_id")
        actor_id = data.get("userId") or data.get("user_id")
        
        if not user_id or not actor_id:
            logger.warning(f"Missing user_id or actor_id in event: {event_type}")
            return
        
        if event_type == "like.created":
            notif = NotificationCreate(
                user_id=user_id,
                type="like",
                title="Nova curtida",
                body="Alguém curtiu seu post",
                actor_id=actor_id,
                target_id=post_id,
                metadata={"post_id": post_id},
            )
            nid = await self.notification_service.create_notification(notif)
            if nid:
                await self.push_service.send_push(user_id, notif.title, notif.body, {"post_id": post_id})
        
        elif event_type == "comment.created":
            content = (data.get("content") or "")[:100]
            # Check for aggregation (multiple comments on same post in 5 min)
            existing = await self.notification_service.get_recent_aggregatable_comment(
                user_id, post_id
            )
            if existing:
                await self.notification_service.aggregate_comment_notification(
                    str(existing["_id"]), 1
                )
                # Update title/body for aggregated
                count = existing.get("aggregated_count", 1) + 1
                title = f"{count} novos comentários"
                body = f"{count} pessoas comentaram no seu post"
                await self.push_service.send_push(user_id, title, body, {"post_id": post_id})
            else:
                notif = NotificationCreate(
                    user_id=user_id,
                    type="comment",
                    title="Novo comentário",
                    body=content or "Alguém comentou no seu post",
                    actor_id=actor_id,
                    target_id=post_id,
                    metadata={"post_id": post_id, "comment_id": data.get("commentId")},
                )
                nid = await self.notification_service.create_notification(notif)
                if nid:
                    await self.push_service.send_push(user_id, notif.title, notif.body, {"post_id": post_id})
    
    async def _handle_social_event(self, event_type: str, data: dict):
        """Handle social.events (follow.created)."""
        if event_type != "follow.created":
            return
        
        # follow.created: followerId follows followingId - notify followingId
        following_id = data.get("followingId") or data.get("following_id")
        follower_id = data.get("followerId") or data.get("follower_id")
        
        if not following_id or not follower_id:
            logger.warning("Missing followingId or followerId in follow.created")
            return
        
        notif = NotificationCreate(
            user_id=following_id,
            type="follow",
            title="Novo seguidor",
            body="Alguém começou a seguir você",
            actor_id=follower_id,
            metadata={"follower_id": follower_id},
        )
        nid = await self.notification_service.create_notification(notif)
        if nid:
            await self.push_service.send_push(following_id, notif.title, notif.body, {"follower_id": follower_id})


# Background thread runner
_consumer_thread = None


def start_consumer_background():
    """Start Kafka consumer in background thread."""
    global _consumer_thread
    import threading
    consumer = NotificationKafkaConsumer()
    _consumer_thread = threading.Thread(target=consumer.start, daemon=True)
    _consumer_thread.start()
    logger.info("Kafka consumer started in background")
    return consumer
