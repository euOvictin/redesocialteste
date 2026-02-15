package com.redesocial.contentservice.service;

import com.redesocial.contentservice.dto.CommentResponse;
import com.redesocial.contentservice.dto.CreateCommentRequest;
import com.redesocial.contentservice.event.CommentCreatedEvent;
import com.redesocial.contentservice.model.jpa.PostMetadata;
import com.redesocial.contentservice.model.mongo.Comment;
import com.redesocial.contentservice.model.mongo.Post;
import com.redesocial.contentservice.repository.jpa.PostMetadataRepository;
import com.redesocial.contentservice.repository.mongo.CommentRepository;
import com.redesocial.contentservice.repository.mongo.PostRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for CommentService
 * Feature: rede-social-brasileira
 */
class CommentServiceProperties {
    
    @Mock
    private CommentRepository commentRepository;
    
    @Mock
    private PostRepository postRepository;
    
    @Mock
    private PostMetadataRepository postMetadataRepository;
    
    @Mock
    private EventPublisher eventPublisher;
    
    private CommentService commentService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        commentService = new CommentService(commentRepository, postRepository, postMetadataRepository, eventPublisher);
    }
    
    /**
     * Property 28: Comentário válido é criado
     * Para qualquer comentário entre 1 e 1000 caracteres, criar comentário deve associá-lo ao post
     * e incrementar contador de comentários
     * **Validates: Requirements 6.4**
     */
    @Property(tries = 100)
    void validCommentIsCreatedAndIncrementsCounter(
            @ForAll @StringLength(min = 1, max = 1000) String content,
            @ForAll String userId,
            @ForAll String postId,
            @ForAll String authorId,
            @ForAll @IntRange(min = 0, max = 100) int initialCommentsCount) {
        
        // Given
        Post post = Post.builder()
                .id(postId)
                .userId(authorId)
                .content("Test post")
                .createdAt(LocalDateTime.now())
                .build();
        
        PostMetadata metadata = PostMetadata.builder()
                .id(postId)
                .userId(authorId)
                .type(PostMetadata.PostType.TEXT)
                .commentsCount(initialCommentsCount)
                .isDeleted(false)
                .build();
        
        Comment savedComment = Comment.builder()
                .id("comment-" + System.nanoTime())
                .postId(postId)
                .userId(userId)
                .content(content)
                .likesCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        CreateCommentRequest request = CreateCommentRequest.builder()
                .userId(userId)
                .content(content)
                .build();
        
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(Optional.of(metadata));
        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        
        // When
        CommentResponse response = commentService.addComment(postId, request);
        
        // Then - Comment is created with correct data
        assertThat(response).isNotNull();
        assertThat(response.getPostId()).isEqualTo(postId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getContent()).isEqualTo(content);
        assertThat(response.getContent().length()).isBetween(1, 1000);
        
        // Verify comment was saved
        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(commentCaptor.capture());
        Comment capturedComment = commentCaptor.getValue();
        assertThat(capturedComment.getPostId()).isEqualTo(postId);
        assertThat(capturedComment.getUserId()).isEqualTo(userId);
        assertThat(capturedComment.getContent()).isEqualTo(content);
        
        // Verify counter was incremented by exactly 1
        ArgumentCaptor<PostMetadata> metadataCaptor = ArgumentCaptor.forClass(PostMetadata.class);
        verify(postMetadataRepository).save(metadataCaptor.capture());
        PostMetadata savedMetadata = metadataCaptor.getValue();
        assertThat(savedMetadata.getCommentsCount()).isEqualTo(initialCommentsCount + 1);
        
        // Verify event was published
        ArgumentCaptor<CommentCreatedEvent> eventCaptor = ArgumentCaptor.forClass(CommentCreatedEvent.class);
        verify(eventPublisher).publishEvent(eq("comment.created"), eventCaptor.capture());
        CommentCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getPostId()).isEqualTo(postId);
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getPostAuthorId()).isEqualTo(authorId);
        assertThat(event.getContent()).isEqualTo(content);
        
        // Reset mocks for next iteration
        reset(commentRepository, postRepository, postMetadataRepository, eventPublisher);
    }
    
    /**
     * Property: Comments are returned in descending order by creation time
     * For any list of comments, they should be ordered from newest to oldest
     */
    @Property(tries = 100)
    void commentsAreReturnedInDescendingOrder(
            @ForAll String postId,
            @ForAll @IntRange(min = 1, max = 20) int commentCount) {
        
        // Given
        Post post = Post.builder()
                .id(postId)
                .userId("author-123")
                .content("Test post")
                .createdAt(LocalDateTime.now())
                .build();
        
        List<Comment> comments = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now();
        
        for (int i = 0; i < commentCount; i++) {
            comments.add(Comment.builder()
                    .id("comment-" + i)
                    .postId(postId)
                    .userId("user-" + i)
                    .content("Comment " + i)
                    .likesCount(0)
                    .createdAt(baseTime.minusHours(commentCount - i))
                    .updatedAt(baseTime.minusHours(commentCount - i))
                    .build());
        }
        
        Page<Comment> commentsPage = new PageImpl<>(comments);
        
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.findByPostIdOrderByCreatedAtDesc(eq(postId), any(Pageable.class)))
                .thenReturn(commentsPage);
        
        // When
        Page<CommentResponse> response = commentService.getComments(postId, 0, 20);
        
        // Then - Comments are ordered by creation time (newest first)
        assertThat(response.getContent()).hasSize(commentCount);
        
        List<CommentResponse> responseList = response.getContent();
        for (int i = 0; i < responseList.size() - 1; i++) {
            LocalDateTime current = responseList.get(i).getCreatedAt();
            LocalDateTime next = responseList.get(i + 1).getCreatedAt();
            // Current should be after or equal to next (descending order)
            assertThat(current).isAfterOrEqualTo(next);
        }
        
        // Reset mocks for next iteration
        reset(commentRepository, postRepository);
    }
    
    /**
     * Property: Pagination returns correct page size
     * For any valid page size, the returned page should not exceed that size
     */
    @Property(tries = 100)
    void paginationReturnsCorrectPageSize(
            @ForAll String postId,
            @ForAll @IntRange(min = 1, max = 100) int pageSize,
            @ForAll @IntRange(min = 0, max = 10) int pageNumber,
            @ForAll @IntRange(min = 0, max = 200) int totalComments) {
        
        // Given
        Post post = Post.builder()
                .id(postId)
                .userId("author-123")
                .content("Test post")
                .createdAt(LocalDateTime.now())
                .build();
        
        // Calculate how many comments should be on this page
        int expectedCommentsOnPage = Math.min(pageSize, Math.max(0, totalComments - (pageNumber * pageSize)));
        
        List<Comment> commentsOnPage = new ArrayList<>();
        for (int i = 0; i < expectedCommentsOnPage; i++) {
            commentsOnPage.add(Comment.builder()
                    .id("comment-" + i)
                    .postId(postId)
                    .userId("user-" + i)
                    .content("Comment " + i)
                    .likesCount(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build());
        }
        
        Page<Comment> commentsPage = new PageImpl<>(commentsOnPage, 
                org.springframework.data.domain.PageRequest.of(pageNumber, pageSize), 
                totalComments);
        
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.findByPostIdOrderByCreatedAtDesc(eq(postId), any(Pageable.class)))
                .thenReturn(commentsPage);
        
        // When
        Page<CommentResponse> response = commentService.getComments(postId, pageNumber, pageSize);
        
        // Then - Page size should not exceed requested size
        assertThat(response.getContent().size()).isLessThanOrEqualTo(pageSize);
        assertThat(response.getContent().size()).isEqualTo(expectedCommentsOnPage);
        assertThat(response.getTotalElements()).isEqualTo(totalComments);
        
        // Reset mocks for next iteration
        reset(commentRepository, postRepository);
    }
}
