package com.redesocial.contentservice.service;

import com.redesocial.contentservice.dto.CreatePostRequest;
import com.redesocial.contentservice.dto.PostResponse;
import com.redesocial.contentservice.event.PostCreatedEvent;
import com.redesocial.contentservice.model.jpa.PostMetadata;
import com.redesocial.contentservice.model.mongo.Post;
import com.redesocial.contentservice.repository.jpa.PostMetadataRepository;
import com.redesocial.contentservice.repository.mongo.PostRepository;
import com.redesocial.contentservice.util.HashtagExtractor;
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
public class PostService {
    
    private final PostRepository postRepository;
    private final PostMetadataRepository postMetadataRepository;
    private final EventPublisher eventPublisher;
    
    @Transactional
    public PostResponse createPost(CreatePostRequest request) {
        log.info("Creating post for user: {}", request.getUserId());
        
        // Generate unique ID
        String postId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        
        // Extract hashtags
        List<String> hashtags = HashtagExtractor.extractHashtags(request.getContent());
        log.debug("Extracted {} hashtags from post", hashtags.size());
        
        // Create MongoDB document
        Post post = Post.builder()
                .id(postId)
                .userId(request.getUserId())
                .content(request.getContent())
                .hashtags(hashtags)
                .createdAt(now)
                .updatedAt(now)
                .build();
        
        postRepository.save(post);
        log.debug("Saved post to MongoDB: {}", postId);
        
        // Create PostgreSQL metadata
        PostMetadata metadata = PostMetadata.builder()
                .id(postId)
                .userId(request.getUserId())
                .type(PostMetadata.PostType.TEXT)
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .isDeleted(false)
                .build();
        
        postMetadataRepository.save(metadata);
        log.debug("Saved post metadata to PostgreSQL: {}", postId);
        
        // Publish event to Kafka
        PostCreatedEvent event = PostCreatedEvent.builder()
                .postId(postId)
                .userId(request.getUserId())
                .content(request.getContent())
                .hashtags(hashtags)
                .type("TEXT")
                .createdAt(now)
                .build();
        
        eventPublisher.publishEvent("post.created", event);
        log.info("Published post.created event for post: {}", postId);
        
        // Build response
        return PostResponse.builder()
                .id(postId)
                .userId(request.getUserId())
                .content(request.getContent())
                .mediaUrls(post.getMediaUrls())
                .hashtags(hashtags)
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
    
    public PostResponse getPost(String postId) {
        log.info("Fetching post: {}", postId);
        
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));
        
        PostMetadata metadata = postMetadataRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new RuntimeException("Post not found or deleted: " + postId));
        
        return PostResponse.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .content(post.getContent())
                .mediaUrls(post.getMediaUrls())
                .hashtags(post.getHashtags())
                .likesCount(metadata.getLikesCount())
                .commentsCount(metadata.getCommentsCount())
                .sharesCount(metadata.getSharesCount())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
