package com.redesocial.contentservice.service;

import com.redesocial.contentservice.event.PostDeletedEvent;
import com.redesocial.contentservice.exception.PostNotFoundException;
import com.redesocial.contentservice.exception.UnauthorizedAccessException;
import com.redesocial.contentservice.model.jpa.PostMetadata;
import com.redesocial.contentservice.repository.jpa.PostMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostDeletionService {
    
    private final PostMetadataRepository postMetadataRepository;
    private final EventPublisher eventPublisher;
    
    @Transactional
    public void deletePost(String postId, String userId) {
        log.info("Deleting post: {} for user: {}", postId, userId);
        
        PostMetadata metadata = postMetadataRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        
        if (!metadata.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("User not authorized to delete this post");
        }
        
        // Soft delete
        metadata.setIsDeleted(true);
        postMetadataRepository.save(metadata);
        
        // Publish event
        PostDeletedEvent event = PostDeletedEvent.builder()
                .postId(postId)
                .userId(userId)
                .deletedAt(LocalDateTime.now())
                .build();
        
        eventPublisher.publishEvent("post.deleted", event);
        log.info("Post deleted and event published: {}", postId);
    }
}
