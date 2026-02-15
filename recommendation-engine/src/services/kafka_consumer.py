"""
Kafka consumer for processing events and invalidating caches.
"""

import json
import logging
from typing import Callable
from confluent_kafka import Consumer, KafkaError, KafkaException
from src.config import settings
from src.services.recommendation_service import RecommendationService

logger = logging.getLogger(__name__)


class KafkaEventConsumer:
    """Kafka consumer for processing content events."""
    
    def __init__(self):
        self.consumer_config = {
            'bootstrap.servers': settings.kafka_bootstrap_servers,
            'group.id': settings.kafka_group_id,
            'auto.offset.reset': settings.kafka_auto_offset_reset,
            'enable.auto.commit': True,
        }
        
        self.consumer = None
        self.recommendation_service = RecommendationService()
        self.running = False
    
    def start(self):
        """Start consuming events from Kafka."""
        try:
            self.consumer = Consumer(self.consumer_config)
            
            # Subscribe to content events topic
            self.consumer.subscribe(['content.events'])
            
            self.running = True
            logger.info("Kafka consumer started, listening for content events")
            
            while self.running:
                msg = self.consumer.poll(timeout=1.0)
                
                if msg is None:
                    continue
                
                if msg.error():
                    if msg.error().code() == KafkaError._PARTITION_EOF:
                        logger.debug(f"Reached end of partition {msg.partition()}")
                    else:
                        logger.error(f"Kafka error: {msg.error()}")
                    continue
                
                # Process message
                try:
                    self._process_message(msg)
                except Exception as e:
                    logger.error(f"Error processing message: {e}", exc_info=True)
        
        except KafkaException as e:
            logger.error(f"Kafka exception: {e}", exc_info=True)
        finally:
            if self.consumer:
                self.consumer.close()
                logger.info("Kafka consumer closed")
    
    def stop(self):
        """Stop consuming events."""
        self.running = False
        logger.info("Stopping Kafka consumer")
    
    def _process_message(self, msg):
        """Process a Kafka message."""
        try:
            # Parse message
            event_data = json.loads(msg.value().decode('utf-8'))
            event_type = event_data.get('event_type')
            
            logger.debug(f"Processing event: {event_type}")
            
            # Handle different event types
            if event_type == 'post.created':
                self._handle_post_created(event_data)
            elif event_type in ['like.created', 'comment.created', 'share.created']:
                self._handle_interaction_event(event_data)
            else:
                logger.debug(f"Ignoring event type: {event_type}")
        
        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse message: {e}")
        except Exception as e:
            logger.error(f"Error processing message: {e}", exc_info=True)
    
    def _handle_post_created(self, event_data):
        """
        Handle post.created event.
        
        Invalidates feed cache for all followers of the post author.
        """
        author_id = event_data.get('user_id')
        post_id = event_data.get('post_id')
        
        if not author_id:
            logger.warning("post.created event missing user_id")
            return
        
        logger.info(f"Handling post.created event: post_id={post_id}, author_id={author_id}")
        
        # Invalidate cache for all followers
        import asyncio
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            invalidated_count = loop.run_until_complete(
                self.recommendation_service.invalidate_followers_cache(author_id)
            )
            logger.info(f"Invalidated cache for {invalidated_count} followers")
        finally:
            loop.close()
    
    def _handle_interaction_event(self, event_data):
        """
        Handle interaction events (like, comment, share).
        
        Updates relevance scores for the affected post.
        """
        post_id = event_data.get('post_id')
        event_type = event_data.get('event_type')
        
        if not post_id:
            logger.warning(f"{event_type} event missing post_id")
            return
        
        logger.info(f"Handling {event_type} event: post_id={post_id}")
        
        # Update score for the post
        import asyncio
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            loop.run_until_complete(
                self.recommendation_service.update_score_from_interaction(post_id, event_type)
            )
        finally:
            loop.close()


def run_consumer():
    """Run the Kafka consumer (entry point for background process)."""
    consumer = KafkaEventConsumer()
    try:
        consumer.start()
    except KeyboardInterrupt:
        logger.info("Received interrupt signal")
        consumer.stop()


if __name__ == "__main__":
    run_consumer()
