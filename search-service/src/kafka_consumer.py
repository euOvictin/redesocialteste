"""Kafka consumer for indexing events"""
import asyncio
import json
import logging
from typing import Optional
from aiokafka import AIOKafkaConsumer
from aiokafka.errors import KafkaError

from src.config import settings
from src.elasticsearch_client import es_client
from src.indexing_service import IndexingService

logger = logging.getLogger(__name__)


class KafkaConsumerService:
    """Kafka consumer service for content and user events"""
    
    def __init__(self):
        self.content_consumer: Optional[AIOKafkaConsumer] = None
        self.user_consumer: Optional[AIOKafkaConsumer] = None
        self.indexing_service: Optional[IndexingService] = None
        self.running = False
    
    async def start(self):
        """Start Kafka consumers"""
        try:
            # Initialize indexing service
            self.indexing_service = IndexingService(es_client.get_client())
            
            # Create content events consumer
            self.content_consumer = AIOKafkaConsumer(
                settings.kafka_content_topic,
                bootstrap_servers=settings.kafka_bootstrap_servers,
                group_id=settings.kafka_consumer_group,
                value_deserializer=lambda m: json.loads(m.decode('utf-8')),
                auto_offset_reset='earliest',
                enable_auto_commit=True,
                max_poll_records=10
            )
            
            # Create user events consumer
            self.user_consumer = AIOKafkaConsumer(
                settings.kafka_user_topic,
                bootstrap_servers=settings.kafka_bootstrap_servers,
                group_id=settings.kafka_consumer_group,
                value_deserializer=lambda m: json.loads(m.decode('utf-8')),
                auto_offset_reset='earliest',
                enable_auto_commit=True,
                max_poll_records=10
            )
            
            # Start consumers
            await self.content_consumer.start()
            await self.user_consumer.start()
            
            self.running = True
            logger.info("Kafka consumers started successfully")
            
            # Start consuming in background tasks
            asyncio.create_task(self._consume_content_events())
            asyncio.create_task(self._consume_user_events())
            
        except KafkaError as e:
            logger.error(f"Kafka error starting consumers: {e}")
            raise
        except Exception as e:
            logger.error(f"Error starting Kafka consumers: {e}")
            raise
    
    async def stop(self):
        """Stop Kafka consumers"""
        self.running = False
        
        try:
            if self.content_consumer:
                await self.content_consumer.stop()
                logger.info("Content consumer stopped")
            
            if self.user_consumer:
                await self.user_consumer.stop()
                logger.info("User consumer stopped")
                
        except Exception as e:
            logger.error(f"Error stopping Kafka consumers: {e}")
    
    async def _consume_content_events(self):
        """Consume content events from Kafka"""
        logger.info(f"Started consuming from topic: {settings.kafka_content_topic}")
        
        retry_count = 0
        max_retries = 3
        
        try:
            async for message in self.content_consumer:
                if not self.running:
                    break
                
                try:
                    event = message.value
                    event_type = event.get('type')
                    
                    logger.info(f"Received content event: {event_type}")
                    
                    if event_type == 'post.created':
                        post_data = event.get('data', {})
                        success = await self.indexing_service.index_post(post_data)
                        
                        if success:
                            retry_count = 0  # Reset retry count on success
                        else:
                            retry_count += 1
                            if retry_count >= max_retries:
                                logger.error(f"Failed to index post after {max_retries} retries")
                                retry_count = 0
                            else:
                                logger.warning(f"Retrying post indexing (attempt {retry_count}/{max_retries})")
                                await asyncio.sleep(2 ** retry_count)  # Exponential backoff
                    
                    else:
                        logger.debug(f"Ignoring event type: {event_type}")
                
                except Exception as e:
                    logger.error(f"Error processing content event: {e}")
                    retry_count += 1
                    if retry_count < max_retries:
                        await asyncio.sleep(2 ** retry_count)
                    else:
                        retry_count = 0
        
        except Exception as e:
            logger.error(f"Fatal error in content consumer: {e}")
            if self.running:
                # Attempt to restart consumer after delay
                await asyncio.sleep(5)
                logger.info("Attempting to restart content consumer...")
                asyncio.create_task(self._consume_content_events())
    
    async def _consume_user_events(self):
        """Consume user events from Kafka"""
        logger.info(f"Started consuming from topic: {settings.kafka_user_topic}")
        
        retry_count = 0
        max_retries = 3
        
        try:
            async for message in self.user_consumer:
                if not self.running:
                    break
                
                try:
                    event = message.value
                    event_type = event.get('type')
                    
                    logger.info(f"Received user event: {event_type}")
                    
                    if event_type == 'user.created':
                        user_data = event.get('data', {})
                        success = await self.indexing_service.index_user(user_data)
                        
                        if success:
                            retry_count = 0  # Reset retry count on success
                        else:
                            retry_count += 1
                            if retry_count >= max_retries:
                                logger.error(f"Failed to index user after {max_retries} retries")
                                retry_count = 0
                            else:
                                logger.warning(f"Retrying user indexing (attempt {retry_count}/{max_retries})")
                                await asyncio.sleep(2 ** retry_count)  # Exponential backoff
                    
                    else:
                        logger.debug(f"Ignoring event type: {event_type}")
                
                except Exception as e:
                    logger.error(f"Error processing user event: {e}")
                    retry_count += 1
                    if retry_count < max_retries:
                        await asyncio.sleep(2 ** retry_count)
                    else:
                        retry_count = 0
        
        except Exception as e:
            logger.error(f"Fatal error in user consumer: {e}")
            if self.running:
                # Attempt to restart consumer after delay
                await asyncio.sleep(5)
                logger.info("Attempting to restart user consumer...")
                asyncio.create_task(self._consume_user_events())


# Global consumer instance
kafka_consumer_service = KafkaConsumerService()

