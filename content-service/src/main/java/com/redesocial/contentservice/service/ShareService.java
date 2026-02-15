package com.redesocial.contentservice.service;

import com.redesocial.contentservice.dto.PostResponse;
import com.redesocial.contentservice.event.ShareCreatedEvent;
import com.redesocial.contentservice.exception.PostNotFoundException;
import com.redesocial.contentservice.model.jpa.PostMetadata;
import com.redesocial.contentservice.model.jpa.Share;
import com.redesocial.contentservice.model.mongo.Post;
import com.redesocial.contentservice.repository.jpa.PostMetadataRepository;
import com.redesocial.contentservice.repository.jpa.ShareRepository;
import com.redesocial.contentservice.repository.mongo.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {
    
    private final PostRepository postRepository;
    private final PostMetadataRepository postMetadataRepository;
    private final ShareRepository shareRepository;
    private final EventPublisher eventPublisher;
    
    @Transactional
    public PostResponse sharePost(String originalPostId, String userId) {
        log.info("User {} attempting to share post {}", userId, originalPostId);
        
        // Verify original post exists and is not deleted
        Post originalPost = postRepository.findById(originalPostId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + originalPostId));
        
        PostMetadata originalMetadata = postMetadataRepository.findByIdAndIsDeletedFalse(originalPostId)
                .orElseThrow(() -> new PostNotFoundException("Post not found or deleted: " + originalPostId));
        
        // Generate unique ID for the shared post
        String sharedPostId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        
        // Create new post in MongoDB that references the original
        Post sharedPost = Post.builder()
                .id(sharedPostId)
                .userId(userId)
                .content("Shared: " + originalPost.getContent())
                .mediaUrls(originalPost.getMediaUrls())
                .hashtags(originalPost.getHashtags())
                .createdAt(now)
                .updatedAt(now)
                .build();
        
        postRepository.save(sharedPost);
        log.debug("Saved shared post to MongoDB: {}", sharedPostId);
        
        // Create metadata for the shared post
        PostMetadata sharedMetadata = PostMetadata.builder()
                .id(sharedPostId)
                .userId(userId)
                .type(originalMetadata.getType())
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .isDeleted(false)
                .build();
        
        postMetadataRepository.save(sharedMetadata);
        log.debug("Saved shared post metadata to PostgreSQL: {}", sharedPostId);
        
        // Create share record
        Share share = Share.builder()
                .originalPostId(originalPostId)
                .sharedPostId(sharedPostId)
                .userId(userId)
                .build();
        
        shareRepository.save(share);
        log.debug("Saved share record linking original {} to shared {}", originalPostId, sharedPostId);
        
        // Increment shares count on original post
        originalMetadata.setSharesCount(originalMetadata.getSharesCount() + 1);
        postMetadataRepository.save(originalMetadata);
        log.info("Incremented shares count for original post {}, new count: {}", 
                originalPostId, originalMetadata.getSharesCount());
        
        // Publish share.created event to Kafka
        ShareCreatedEvent event = ShareCreatedEvent.builder()
                .originalPostId(originalPostId)
                .sharedPostId(sharedPostId)
                .userId(userId)
                .originalAuthorId(originalPost.getUserId())
                .createdAt(now)
                .build();
        
        eventPublisher.publishEvent("share.created", event);
        log.info("Published share.created event for shared post: {}", sharedPostId);
        
        // Build response for the shared post
        return PostResponse.builder()
                .id(sharedPostId)
                .userId(userId)
                .content(sharedPost.getContent())
                .mediaUrls(sharedPost.getMediaUrls())
                .hashtags(sharedPost.getHashtags())
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
