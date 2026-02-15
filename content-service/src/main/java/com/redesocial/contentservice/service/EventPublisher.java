package com.redesocial.contentservice.service;

import com.redesocial.contentservice.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public void publishEvent(String eventType, Object event) {
        try {
            kafkaTemplate.send(KafkaConfig.CONTENT_EVENTS_TOPIC, eventType, event);
            log.info("Published event: {} to topic: {}", eventType, KafkaConfig.CONTENT_EVENTS_TOPIC);
        } catch (Exception e) {
            log.error("Failed to publish event: {}", eventType, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}
