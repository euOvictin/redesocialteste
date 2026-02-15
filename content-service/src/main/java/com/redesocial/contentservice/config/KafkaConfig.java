package com.redesocial.contentservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
    
    public static final String CONTENT_EVENTS_TOPIC = "content.events";
    
    @Bean
    public NewTopic contentEventsTopic() {
        return TopicBuilder.name(CONTENT_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
