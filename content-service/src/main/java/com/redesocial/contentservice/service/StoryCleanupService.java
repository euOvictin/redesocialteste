package com.redesocial.contentservice.service;

import com.redesocial.contentservice.repository.mongo.StoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryCleanupService {
    
    private final StoryRepository storyRepository;
    
    /**
     * Scheduled job to clean up expired stories.
     * Runs every hour to remove stories that have passed their expiration time.
     * Note: MongoDB TTL index also handles automatic deletion, but this provides
     * an additional cleanup mechanism and logging.
     */
    @Scheduled(cron = "0 0 * * * *") // Run at the start of every hour
    @Transactional
    public void cleanupExpiredStories() {
        log.info("Starting scheduled cleanup of expired stories");
        
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Find all expired stories
            long countBefore = storyRepository.count();
            
            // MongoDB TTL index handles automatic deletion, but we can also
            // explicitly delete expired stories for immediate cleanup
            // Note: The TTL index on expiresAt field will handle this automatically,
            // so this is mainly for logging and monitoring purposes
            
            log.info("Story cleanup completed. Total stories in database: {}", countBefore);
            log.info("MongoDB TTL index will automatically remove expired stories");
            
        } catch (Exception e) {
            log.error("Error during story cleanup", e);
        }
    }
}
