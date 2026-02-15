package com.redesocial.contentservice.service;

import com.redesocial.contentservice.event.LikeCreatedEvent;
import com.redesocial.contentservice.exception.PostNotFoundException;
import com.redesocial.contentservice.model.jpa.Like;
import com.redesocial.contentservice.model.jpa.PostMetadata;
import com.redesocial.contentservice.repository.jpa.LikeRepository;
import com.redesocial.contentservice.repository.jpa.PostMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LikeService
 */
class LikeServiceTest {
    
    private LikeRepository likeRepository;
    private PostMetadataRepository postMetadataRepository;
    private EventPublisher eventPublisher;
    private LikeService likeService;
    
    @BeforeEach
    void setUp() {
        likeRepository = Mockito.mock(LikeRepository.class);
        postMetadataRepository = Mockito.mock(PostMetadataRepository.class);
        eventPublisher = Mockito.mock(EventPublisher.class);
        likeService = new LikeService(likeRepository, postMetadataRepository, eventPublisher);
    }
    
    @Test
    void likePost_whenPostNotFound_shouldThrowException() {
        // Arrange
        String postId = "nonexistent";
        String userId = "user123";
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThatThrownBy(() -> likeService.likePost(postId, userId))
                .isInstanceOf(PostNotFoundException.class)
                .hasMessageContaining("Post not found");
    }
    
    @Test
    void likePost_whenNotYetLiked_shouldCreateLikeAndIncrementCounter() {
        // Arrange
        String postId = "post123";
        String userId = "user456";
        PostMetadata postMetadata = PostMetadata.builder()
                .id(postId)
                .userId("author789")
                .likesCount(5)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(postMetadata));
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false);
        when(likeRepository.save(any(Like.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        likeService.likePost(postId, userId);
        
        // Assert
        verify(likeRepository).save(any(Like.class));
        assertThat(postMetadata.getLikesCount()).isEqualTo(6);
        verify(postMetadataRepository).save(postMetadata);
        verify(eventPublisher).publishEvent(eq("like.created"), any(LikeCreatedEvent.class));
    }
    
    @Test
    void likePost_whenAlreadyLiked_shouldBeIdempotent() {
        // Arrange
        String postId = "post123";
        String userId = "user456";
        PostMetadata postMetadata = PostMetadata.builder()
                .id(postId)
                .userId("author789")
                .likesCount(5)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(postMetadata));
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(true);
        
        // Act
        likeService.likePost(postId, userId);
        
        // Assert
        verify(likeRepository, never()).save(any(Like.class));
        assertThat(postMetadata.getLikesCount()).isEqualTo(5); // Counter unchanged
        verify(postMetadataRepository, never()).save(any(PostMetadata.class));
        verify(eventPublisher, never()).publishEvent(anyString(), any());
    }
    
    @Test
    void likePost_shouldPublishEventWithCorrectData() {
        // Arrange
        String postId = "post123";
        String userId = "user456";
        String authorId = "author789";
        PostMetadata postMetadata = PostMetadata.builder()
                .id(postId)
                .userId(authorId)
                .likesCount(0)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(postMetadata));
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false);
        when(likeRepository.save(any(Like.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        likeService.likePost(postId, userId);
        
        // Assert
        ArgumentCaptor<LikeCreatedEvent> eventCaptor = ArgumentCaptor.forClass(LikeCreatedEvent.class);
        verify(eventPublisher).publishEvent(eq("like.created"), eventCaptor.capture());
        
        LikeCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getPostId()).isEqualTo(postId);
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getPostAuthorId()).isEqualTo(authorId);
        assertThat(event.getCreatedAt()).isNotNull();
    }
    
    @Test
    void unlikePost_whenPostNotFound_shouldThrowException() {
        // Arrange
        String postId = "nonexistent";
        String userId = "user123";
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThatThrownBy(() -> likeService.unlikePost(postId, userId))
                .isInstanceOf(PostNotFoundException.class)
                .hasMessageContaining("Post not found");
    }
    
    @Test
    void unlikePost_whenLikeExists_shouldDeleteLikeAndDecrementCounter() {
        // Arrange
        String postId = "post123";
        String userId = "user456";
        PostMetadata postMetadata = PostMetadata.builder()
                .id(postId)
                .userId("author789")
                .likesCount(5)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(postMetadata));
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(true);
        doNothing().when(likeRepository).deleteByPostIdAndUserId(postId, userId);
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        likeService.unlikePost(postId, userId);
        
        // Assert
        verify(likeRepository).deleteByPostIdAndUserId(postId, userId);
        assertThat(postMetadata.getLikesCount()).isEqualTo(4);
        verify(postMetadataRepository).save(postMetadata);
    }
    
    @Test
    void unlikePost_whenLikeDoesNotExist_shouldBeIdempotent() {
        // Arrange
        String postId = "post123";
        String userId = "user456";
        PostMetadata postMetadata = PostMetadata.builder()
                .id(postId)
                .userId("author789")
                .likesCount(5)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(postMetadata));
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false);
        
        // Act
        likeService.unlikePost(postId, userId);
        
        // Assert
        verify(likeRepository, never()).deleteByPostIdAndUserId(anyString(), anyString());
        assertThat(postMetadata.getLikesCount()).isEqualTo(5); // Counter unchanged
        verify(postMetadataRepository, never()).save(any(PostMetadata.class));
    }
    
    @Test
    void unlikePost_whenCounterIsZero_shouldNotGoBelowZero() {
        // Arrange
        String postId = "post123";
        String userId = "user456";
        PostMetadata postMetadata = PostMetadata.builder()
                .id(postId)
                .userId("author789")
                .likesCount(0)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(postMetadata));
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(true);
        doNothing().when(likeRepository).deleteByPostIdAndUserId(postId, userId);
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        likeService.unlikePost(postId, userId);
        
        // Assert
        assertThat(postMetadata.getLikesCount()).isEqualTo(0); // Should not go negative
        verify(postMetadataRepository).save(postMetadata);
    }
    
    @Test
    void likePost_multipleUsers_shouldIncrementCounterCorrectly() {
        // Arrange
        String postId = "post123";
        PostMetadata postMetadata = PostMetadata.builder()
                .id(postId)
                .userId("author789")
                .likesCount(0)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(postMetadata));
        when(likeRepository.existsByPostIdAndUserId(eq(postId), anyString())).thenReturn(false);
        when(likeRepository.save(any(Like.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        likeService.likePost(postId, "user1");
        likeService.likePost(postId, "user2");
        likeService.likePost(postId, "user3");
        
        // Assert
        assertThat(postMetadata.getLikesCount()).isEqualTo(3);
        verify(likeRepository, times(3)).save(any(Like.class));
        verify(eventPublisher, times(3)).publishEvent(eq("like.created"), any(LikeCreatedEvent.class));
    }
}
