package com.redesocial.contentservice.service;

import com.redesocial.contentservice.exception.StoryNotFoundException;
import com.redesocial.contentservice.model.mongo.Post;
import com.redesocial.contentservice.model.mongo.Story;
import com.redesocial.contentservice.model.mongo.StoryView;
import com.redesocial.contentservice.repository.mongo.StoryRepository;
import com.redesocial.contentservice.repository.mongo.StoryViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryService {
    
    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    
    private static final int STORY_EXPIRATION_HOURS = 24;
    
    @Transactional
    public Story createStory(String userId, Post.MediaUrl mediaUrl) {
        log.info("Creating story for user: {}", userId);
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(STORY_EXPIRATION_HOURS);
        
        Story story = Story.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .mediaUrl(mediaUrl)
                .viewsCount(0)
                .expiresAt(expiresAt)
                .createdAt(now)
                .build();
        
        storyRepository.save(story);
        log.info("Created story: {} with expiration: {}", story.getId(), expiresAt);
        
        return story;
    }
    
    public List<Story> getActiveStoriesByUser(String userId) {
        log.info("Fetching active stories for user: {}", userId);
        LocalDateTime now = LocalDateTime.now();
        return storyRepository.findActiveStoriesByUserId(userId, now);
    }
    
    @Transactional
    public void recordStoryView(String storyId, String viewerId) {
        log.info("Recording view for story: {} by viewer: {}", storyId, viewerId);
        
        // Check if already viewed
        if (storyViewRepository.findByStoryIdAndViewerId(storyId, viewerId).isPresent()) {
            log.debug("Story already viewed by this user");
            return;
        }
        
        // Record view
        StoryView view = StoryView.builder()
                .id(UUID.randomUUID().toString())
                .storyId(storyId)
                .viewerId(viewerId)
                .viewedAt(LocalDateTime.now())
                .build();
        
        storyViewRepository.save(view);
        
        // Update story views count
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException(storyId));
        
        story.setViewsCount(story.getViewsCount() + 1);
        storyRepository.save(story);
        
        log.info("Recorded view for story: {}", storyId);
    }
    
    public List<StoryView> getStoryViewers(String storyId) {
        log.info("Fetching viewers for story: {}", storyId);
        LocalDateTime since = LocalDateTime.now().minusHours(STORY_EXPIRATION_HOURS);
        return storyViewRepository.findRecentViewsByStoryId(storyId, since);
    }
    
    @Transactional
    public void deleteStory(String storyId) {
        log.info("Deleting story: {}", storyId);
        storyRepository.deleteById(storyId);
    }
}
