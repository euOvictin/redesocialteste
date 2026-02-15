"""Integration tests for Kafka consumer"""
import pytest
import asyncio
from unittest.mock import Mock, AsyncMock, patch
import json

from src.kafka_consumer import KafkaConsumerService
from src.indexing_service import IndexingService


@pytest.fixture
def mock_indexing_service():
    """Mock indexing service"""
    service = Mock(spec=IndexingService)
    service.index_post = AsyncMock(return_value=True)
    service.index_user = AsyncMock(return_value=True)
    return service


@pytest.fixture
def kafka_consumer():
    """Create Kafka consumer service"""
    return KafkaConsumerService()


class TestKafkaConsumer:
    """Test Kafka consumer functionality"""
    
    @pytest.mark.asyncio
    async def test_consumer_starts_successfully(self, kafka_consumer):
        """Test that consumer starts without errors"""
        with patch('src.kafka_consumer.AIOKafkaConsumer') as mock_consumer_class:
            mock_content_consumer = AsyncMock()
            mock_user_consumer = AsyncMock()
            mock_consumer_class.side_effect = [mock_content_consumer, mock_user_consumer]
            
            with patch('src.kafka_consumer.es_client') as mock_es:
                mock_es.get_client.return_value = Mock()
                
                await kafka_consumer.start()
                
                assert kafka_consumer.running is True
                assert mock_content_consumer.start.called
                assert mock_user_consumer.start.called
    
    @pytest.mark.asyncio
    async def test_consumer_stops_successfully(self, kafka_consumer):
        """Test that consumer stops cleanly"""
        kafka_consumer.content_consumer = AsyncMock()
        kafka_consumer.user_consumer = AsyncMock()
        kafka_consumer.running = True
        
        await kafka_consumer.stop()
        
        assert kafka_consumer.running is False
        assert kafka_consumer.content_consumer.stop.called
        assert kafka_consumer.user_consumer.stop.called
    
    @pytest.mark.asyncio
    async def test_process_post_created_event(self, kafka_consumer, mock_indexing_service):
        """Test processing post.created event"""
        kafka_consumer.indexing_service = mock_indexing_service
        kafka_consumer.running = True
        
        # Create mock message
        event = {
            'type': 'post.created',
            'data': {
                'id': 'post-123',
                'user_id': 'user-456',
                'content': 'Test post with #hashtag',
                'created_at': '2024-01-01T00:00:00Z'
            }
        }
        
        mock_message = Mock()
        mock_message.value = event
        
        # Mock the consumer to return one message then stop
        async def mock_consumer_iter():
            yield mock_message
            kafka_consumer.running = False
        
        kafka_consumer.content_consumer = Mock()
        kafka_consumer.content_consumer.__aiter__ = lambda self: mock_consumer_iter()
        
        # Process events
        await kafka_consumer._consume_content_events()
        
        # Verify indexing was called
        mock_indexing_service.index_post.assert_called_once()
        call_args = mock_indexing_service.index_post.call_args[0][0]
        assert call_args['id'] == 'post-123'
    
    @pytest.mark.asyncio
    async def test_process_user_created_event(self, kafka_consumer, mock_indexing_service):
        """Test processing user.created event"""
        kafka_consumer.indexing_service = mock_indexing_service
        kafka_consumer.running = True
        
        # Create mock message
        event = {
            'type': 'user.created',
            'data': {
                'id': 'user-123',
                'email': 'test@example.com',
                'name': 'Test User',
                'created_at': '2024-01-01T00:00:00Z'
            }
        }
        
        mock_message = Mock()
        mock_message.value = event
        
        # Mock the consumer to return one message then stop
        async def mock_consumer_iter():
            yield mock_message
            kafka_consumer.running = False
        
        kafka_consumer.user_consumer = Mock()
        kafka_consumer.user_consumer.__aiter__ = lambda self: mock_consumer_iter()
        
        # Process events
        await kafka_consumer._consume_user_events()
        
        # Verify indexing was called
        mock_indexing_service.index_user.assert_called_once()
        call_args = mock_indexing_service.index_user.call_args[0][0]
        assert call_args['id'] == 'user-123'
    
    @pytest.mark.asyncio
    async def test_ignore_unknown_event_types(self, kafka_consumer, mock_indexing_service):
        """Test that unknown event types are ignored"""
        kafka_consumer.indexing_service = mock_indexing_service
        kafka_consumer.running = True
        
        # Create mock message with unknown event type
        event = {
            'type': 'post.deleted',
            'data': {'id': 'post-123'}
        }
        
        mock_message = Mock()
        mock_message.value = event
        
        # Mock the consumer to return one message then stop
        async def mock_consumer_iter():
            yield mock_message
            kafka_consumer.running = False
        
        kafka_consumer.content_consumer = Mock()
        kafka_consumer.content_consumer.__aiter__ = lambda self: mock_consumer_iter()
        
        # Process events
        await kafka_consumer._consume_content_events()
        
        # Verify indexing was NOT called
        mock_indexing_service.index_post.assert_not_called()
    
    @pytest.mark.asyncio
    async def test_retry_on_indexing_failure(self, kafka_consumer, mock_indexing_service):
        """Test retry logic when indexing fails"""
        kafka_consumer.indexing_service = mock_indexing_service
        kafka_consumer.running = True
        
        # Make indexing fail first time, succeed second time
        mock_indexing_service.index_post.side_effect = [False, True]
        
        # Create mock messages
        event = {
            'type': 'post.created',
            'data': {
                'id': 'post-123',
                'user_id': 'user-456',
                'content': 'Test post',
                'created_at': '2024-01-01T00:00:00Z'
            }
        }
        
        mock_message = Mock()
        mock_message.value = event
        
        # Mock the consumer to return two messages then stop
        async def mock_consumer_iter():
            yield mock_message
            yield mock_message
            kafka_consumer.running = False
        
        kafka_consumer.content_consumer = Mock()
        kafka_consumer.content_consumer.__aiter__ = lambda self: mock_consumer_iter()
        
        # Process events
        await kafka_consumer._consume_content_events()
        
        # Verify indexing was called twice (initial + retry)
        assert mock_indexing_service.index_post.call_count == 2


class TestErrorHandling:
    """Test error handling in Kafka consumer"""
    
    @pytest.mark.asyncio
    async def test_handle_malformed_message(self, kafka_consumer, mock_indexing_service):
        """Test handling of malformed messages"""
        kafka_consumer.indexing_service = mock_indexing_service
        kafka_consumer.running = True
        
        # Create mock message with missing data
        event = {
            'type': 'post.created'
            # Missing 'data' field
        }
        
        mock_message = Mock()
        mock_message.value = event
        
        # Mock the consumer to return one message then stop
        async def mock_consumer_iter():
            yield mock_message
            kafka_consumer.running = False
        
        kafka_consumer.content_consumer = Mock()
        kafka_consumer.content_consumer.__aiter__ = lambda self: mock_consumer_iter()
        
        # Process events - should not crash
        await kafka_consumer._consume_content_events()
        
        # Verify indexing was called with empty dict
        mock_indexing_service.index_post.assert_called_once()
    
    @pytest.mark.asyncio
    async def test_handle_exception_during_processing(self, kafka_consumer, mock_indexing_service):
        """Test handling of exceptions during message processing"""
        kafka_consumer.indexing_service = mock_indexing_service
        kafka_consumer.running = True
        
        # Make indexing raise an exception
        mock_indexing_service.index_post.side_effect = Exception("Test error")
        
        event = {
            'type': 'post.created',
            'data': {'id': 'post-123'}
        }
        
        mock_message = Mock()
        mock_message.value = event
        
        # Mock the consumer to return one message then stop
        async def mock_consumer_iter():
            yield mock_message
            kafka_consumer.running = False
        
        kafka_consumer.content_consumer = Mock()
        kafka_consumer.content_consumer.__aiter__ = lambda self: mock_consumer_iter()
        
        # Process events - should not crash
        await kafka_consumer._consume_content_events()
        
        # Verify indexing was attempted
        mock_indexing_service.index_post.assert_called()

