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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {
    
    @Mock
    private CommentRepository commentRepository;
    
    @Mock
    private PostRepository postRepository;
    
    @Mock
    private PostMetadataRepository postMetadataRepository;
    
    @Mock
    private EventPublisher eventPublisher;
    
    @InjectMocks
    private CommentService commentService;
    
    private Post testPost;
    private PostMetadata testMetadata;
    private Comment testComment;
    
    @BeforeEach
    void setUp() {
        testPost = Post.builder()
                .id("post-123")
                .userId("author-456")
                .content("Test post")
                .createdAt(LocalDateTime.now())
                .build();
        
        testMetadata = PostMetadata.builder()
                .id("post-123")
                .userId("author-456")
                .type(PostMetadata.PostType.TEXT)
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .isDeleted(false)
                .build();
        
        testComment = Comment.builder()
                .id("comment-789")
                .postId("post-123")
                .userId("user-999")
                .content("Great post!")
                .likesCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    @Test
    void addComment_WithValidData_CreatesCommentAndIncrementsCounter() {
        // Given
        CreateCommentRequest request = CreateCommentRequest.builder()
                .userId("user-999")
                .content("Great post!")
                .build();
        
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse("post-123")).thenReturn(Optional.of(testMetadata));
        when(commentRepository.save(any(Comment.class))).thenReturn(testComment);
        
        // When
        CommentResponse response = commentService.addComment("post-123", request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("comment-789");
        assertThat(response.getPostId()).isEqualTo("post-123");
        assertThat(response.getUserId()).isEqualTo("user-999");
        assertThat(response.getContent()).isEqualTo("Great post!");
        
        // Verify comment was saved
        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(commentCaptor.capture());
        Comment savedComment = commentCaptor.getValue();
        assertThat(savedComment.getPostId()).isEqualTo("post-123");
        assertThat(savedComment.getUserId()).isEqualTo("user-999");
        assertThat(savedComment.getContent()).isEqualTo("Great post!");
        
        // Verify counter was incremented
        ArgumentCaptor<PostMetadata> metadataCaptor = ArgumentCaptor.forClass(PostMetadata.class);
        verify(postMetadataRepository).save(metadataCaptor.capture());
        PostMetadata savedMetadata = metadataCaptor.getValue();
        assertThat(savedMetadata.getCommentsCount()).isEqualTo(1);
        
        // Verify event was published
        ArgumentCaptor<CommentCreatedEvent> eventCaptor = ArgumentCaptor.forClass(CommentCreatedEvent.class);
        verify(eventPublisher).publishEvent(eq("comment.created"), eventCaptor.capture());
        CommentCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getCommentId()).isEqualTo("comment-789");
        assertThat(event.getPostId()).isEqualTo("post-123");
        assertThat(event.getUserId()).isEqualTo("user-999");
        assertThat(event.getPostAuthorId()).isEqualTo("author-456");
    }
    
    @Test
    void addComment_WithNonExistentPost_ThrowsPostNotFoundException() {
        // Given
        CreateCommentRequest request = CreateCommentRequest.builder()
                .userId("user-999")
                .content("Great post!")
                .build();
        
        when(postRepository.findById("non-existent")).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> commentService.addComment("non-existent", request))
                .isInstanceOf(PostNotFoundException.class)
                .hasMessageContaining("Post not found");
        
        verify(commentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(), any());
    }
    
    @Test
    void addComment_WithDeletedPost_ThrowsPostNotFoundException() {
        // Given
        CreateCommentRequest request = CreateCommentRequest.builder()
                .userId("user-999")
                .content("Great post!")
                .build();
        
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse("post-123")).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> commentService.addComment("post-123", request))
                .isInstanceOf(PostNotFoundException.class)
                .hasMessageContaining("Post not found or deleted");
        
        verify(commentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(), any());
    }
    
    @Test
    void addComment_WithMinimumLength_CreatesComment() {
        // Given
        CreateCommentRequest request = CreateCommentRequest.builder()
                .userId("user-999")
                .content("A")
                .build();
        
        Comment shortComment = Comment.builder()
                .id("comment-short")
                .postId("post-123")
                .userId("user-999")
                .content("A")
                .likesCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse("post-123")).thenReturn(Optional.of(testMetadata));
        when(commentRepository.save(any(Comment.class))).thenReturn(shortComment);
        
        // When
        CommentResponse response = commentService.addComment("post-123", request);
        
        // Then
        assertThat(response.getContent()).isEqualTo("A");
        verify(commentRepository).save(any(Comment.class));
    }
    
    @Test
    void addComment_WithMaximumLength_CreatesComment() {
        // Given
        String maxContent = "A".repeat(1000);
        CreateCommentRequest request = CreateCommentRequest.builder()
                .userId("user-999")
                .content(maxContent)
                .build();
        
        Comment longComment = Comment.builder()
                .id("comment-long")
                .postId("post-123")
                .userId("user-999")
                .content(maxContent)
                .likesCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse("post-123")).thenReturn(Optional.of(testMetadata));
        when(commentRepository.save(any(Comment.class))).thenReturn(longComment);
        
        // When
        CommentResponse response = commentService.addComment("post-123", request);
        
        // Then
        assertThat(response.getContent()).hasSize(1000);
        verify(commentRepository).save(any(Comment.class));
    }
    
    @Test
    void getComments_WithValidPostId_ReturnsPagedComments() {
        // Given
        Comment comment1 = Comment.builder()
                .id("comment-1")
                .postId("post-123")
                .userId("user-1")
                .content("First comment")
                .likesCount(0)
                .createdAt(LocalDateTime.now().minusHours(2))
                .updatedAt(LocalDateTime.now().minusHours(2))
                .build();
        
        Comment comment2 = Comment.builder()
                .id("comment-2")
                .postId("post-123")
                .userId("user-2")
                .content("Second comment")
                .likesCount(0)
                .createdAt(LocalDateTime.now().minusHours(1))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();
        
        Page<Comment> commentsPage = new PageImpl<>(Arrays.asList(comment2, comment1));
        
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(commentRepository.findByPostIdOrderByCreatedAtDesc(eq("post-123"), any(Pageable.class)))
                .thenReturn(commentsPage);
        
        // When
        Page<CommentResponse> response = commentService.getComments("post-123", 0, 20);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getId()).isEqualTo("comment-2");
        assertThat(response.getContent().get(1).getId()).isEqualTo("comment-1");
        
        verify(commentRepository).findByPostIdOrderByCreatedAtDesc(eq("post-123"), any(Pageable.class));
    }
    
    @Test
    void getComments_WithNonExistentPost_ThrowsPostNotFoundException() {
        // Given
        when(postRepository.findById("non-existent")).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> commentService.getComments("non-existent", 0, 20))
                .isInstanceOf(PostNotFoundException.class)
                .hasMessageContaining("Post not found");
        
        verify(commentRepository, never()).findByPostIdOrderByCreatedAtDesc(any(), any());
    }
    
    @Test
    void getComments_WithCustomPageSize_ReturnsCorrectPage() {
        // Given
        Pageable pageable = PageRequest.of(1, 10);
        Page<Comment> commentsPage = new PageImpl<>(Arrays.asList(testComment), pageable, 15);
        
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(commentRepository.findByPostIdOrderByCreatedAtDesc(eq("post-123"), any(Pageable.class)))
                .thenReturn(commentsPage);
        
        // When
        Page<CommentResponse> response = commentService.getComments("post-123", 1, 10);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getNumber()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(15);
        
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findByPostIdOrderByCreatedAtDesc(eq("post-123"), pageableCaptor.capture());
        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageNumber()).isEqualTo(1);
        assertThat(capturedPageable.getPageSize()).isEqualTo(10);
    }
}
