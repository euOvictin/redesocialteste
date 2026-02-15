package com.redesocial.contentservice.service;

import com.redesocial.contentservice.event.LikeCreatedEvent;
import com.redesocial.contentservice.exception.PostNotFoundException;
import com.redesocial.contentservice.model.jpa.Like;
import com.redesocial.contentservice.model.jpa.PostMetadata;
import com.redesocial.contentservice.repository.jpa.LikeRepository;
import com.redesocial.contentservice.repository.jpa.PostMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {
    
    private final LikeRepository likeRepository;
    private final PostMetadataRepository postMetadataRepository;
    private final EventPublisher eventPublisher;
    
    @Transactional
    public void likePost(String postId, String userId) {
        log.info("User {} attempting to like post {}", userId, postId);
        
        // Verify post exists
        PostMetadata postMetadata = postMetadataRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));
        
        // Check if already liked (idempotency)
        if (likeRepository.existsByPostIdAndUserId(postId, userId)) {
            log.info("User {} already liked post {}, treating as idempotent operation", userId, postId);
            return;
        }
        
        // Create like
        Like like = Like.builder()
                .postId(postId)
                .userId(userId)
                .build();
        likeRepository.save(like);
        
        // Increment likes count
        postMetadata.setLikesCount(postMetadata.getLikesCount() + 1);
        postMetadataRepository.save(postMetadata);
        
        // Publish event
        LikeCreatedEvent event = LikeCreatedEvent.builder()
                .postId(postId)
                .userId(userId)
                .postAuthorId(postMetadata.getUserId())
                .createdAt(LocalDateTime.now())
                .build();
        eventPublisher.publishEvent("like.created", event);
        
        log.info("User {} successfully liked post {}, new count: {}", 
                userId, postId, postMetadata.getLikesCount());
    }
    
    @Transactional
    public void unlikePost(String postId, String userId) {
        log.info("User {} attempting to unlike post {}", userId, postId);
        
        // Verify post exists
        PostMetadata postMetadata = postMetadataRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));
        
        // Check if like exists
        if (!likeRepository.existsByPostIdAndUserId(postId, userId)) {
            log.info("User {} has not liked post {}, treating as idempotent operation", userId, postId);
            return;
        }
        
        // Delete like
        likeRepository.deleteByPostIdAndUserId(postId, userId);
        
        // Decrement likes count (ensure it doesn't go below 0)
        int currentCount = postMetadata.getLikesCount();
        postMetadata.setLikesCount(Math.max(0, currentCount - 1));
        postMetadataRepository.save(postMetadata);
        
        log.info("User {} successfully unliked post {}, new count: {}", 
                userId, postId, postMetadata.getLikesCount());
    }
}
