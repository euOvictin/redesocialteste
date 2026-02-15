package com.redesocial.contentservice.service;

import com.redesocial.contentservice.dto.CommentResponse;
import com.redesocial.contentservice.dto.CreateCommentRequest;
import com.redesocial.contentservice.event.CommentCreatedEvent;
import com.redesocial.contentservice.exception.PostNotFoundException;
import com.redesocial.contentservice.model.jpa.PostMetadata;
import com.redesocial.contentservice.model.mongo.Comment;
import com.redesocial.contentservice.model.mongo.Post;
import com.redesocial.contentservice.repository.jpa.PostMetadataRepository;
import com.redesocial.contentservice.repository.mongo.CommentRepository;
import com.redesocial.contentservice.repository.mongo.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {
    
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final PostMetadataRepository postMetadataRepository;
    private final EventPublisher eventPublisher;
    
    @Transactional
    public CommentResponse addComment(String postId, CreateCommentRequest request) {
        log.info("Adding comment to post: {} by user: {}", postId, request.getUserId());
        
        // Verify post exists
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));
        
        PostMetadata metadata = postMetadataRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found or deleted: " + postId));
        
        // Create comment
        Comment comment = Comment.builder()
                .postId(postId)
                .userId(request.getUserId())
                .content(request.getContent())
                .likesCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        comment = commentRepository.save(comment);
        log.info("Comment created with ID: {}", comment.getId());
        
        // Increment comments count
        metadata.setCommentsCount(metadata.getCommentsCount() + 1);
        postMetadataRepository.save(metadata);
        log.info("Incremented comments count for post: {} to {}", postId, metadata.getCommentsCount());
        
        // Publish event
        CommentCreatedEvent event = CommentCreatedEvent.builder()
                .commentId(comment.getId())
                .postId(postId)
                .userId(request.getUserId())
                .postAuthorId(post.getUserId())
                .content(request.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
        
        eventPublisher.publishEvent("comment.created", event);
        
        return mapToResponse(comment);
    }
    
    public Page<CommentResponse> getComments(String postId, int page, int size) {
        log.info("Fetching comments for post: {} (page: {}, size: {})", postId, page, size);
        
        // Verify post exists
        postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtDesc(postId, pageable);
        
        log.info("Found {} comments for post: {}", comments.getTotalElements(), postId);
        return comments.map(this::mapToResponse);
    }
    
    private CommentResponse mapToResponse(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .userId(comment.getUserId())
                .content(comment.getContent())
                .likesCount(comment.getLikesCount())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
