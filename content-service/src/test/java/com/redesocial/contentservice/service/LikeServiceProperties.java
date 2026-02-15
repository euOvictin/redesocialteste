package com.redesocial.contentservice.service;

import com.redesocial.contentservice.event.LikeCreatedEvent;
import com.redesocial.contentservice.model.jpa.Like;
import com.redesocial.contentservice.model.jpa.PostMetadata;
import com.redesocial.contentservice.repository.jpa.LikeRepository;
import com.redesocial.contentservice.repository.jpa.PostMetadataRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for LikeService
 * Feature: rede-social-brasileira
 */
class LikeServiceProperties {
    
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
    
    /**
     * Property 25: Curtir post incrementa contador
     * **Validates: Requirements 6.1**
     * 
     * Para qualquer post, curtir deve incrementar o contador de curtidas em exatamente 1
     */
    @Property(tries = 100)
    void likingPostIncrementsCounter(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String postId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String authorId,
            @ForAll @IntRange(min = 0, max = 1000) int initialLikesCount
    ) {
        // Arrange
        PostMetadata postMetadata = PostMetadata.builder()
                .id(postId)
                .userId(authorId)
                .likesCount(initialLikesCount)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(postMetadata));
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false);
        when(likeRepository.save(any(Like.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        likeService.likePost(postId, userId);
        
        // Assert
        assertThat(postMetadata.getLikesCount()).isEqualTo(initialLikesCount + 1);
        verify(likeRepository, times(1)).save(any(Like.class));
        verify(postMetadataRepository, times(1)).save(postMetadata);
    }
    
    /**
     * Property 26: Descurtir post decrementa contador
     * **Validates: Requirements 6.2**
     * 
     * Para qualquer post curtido, remover curtida deve decrementar o contador em exatamente 1
     */
    @Property(tries = 100)
    void unlikingPostDecrementsCounter(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String postId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String authorId,
            @ForAll @IntRange(min = 1, max = 1000) int initialLikesCount
    ) {
        // Arrange
        PostMetadata postMetadata = PostMetadata.builder()
                .id(postId)
                .userId(authorId)
                .likesCount(initialLikesCount)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(postMetadata));
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(true);
        doNothing().when(likeRepository).deleteByPostIdAndUserId(postId, userId);
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        likeService.unlikePost(postId, userId);
        
        // Assert
        assertThat(postMetadata.getLikesCount()).isEqualTo(initialLikesCount - 1);
        verify(likeRepository, times(1)).deleteByPostIdAndUserId(postId, userId);
        verify(postMetadataRepository, times(1)).save(postMetadata);
    }
    
    /**
     * Property 27: Curtir é idempotente
     * **Validates: Requirements 6.3**
     * 
     * Para qualquer post, curtir múltiplas vezes deve ter o mesmo efeito que curtir uma vez 
     * (contador incrementa apenas 1)
     */
    @Property(tries = 100)
    void likingIsIdempotent(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String postId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String authorId,
            @ForAll @IntRange(min = 0, max = 1000) int initialLikesCount
    ) {
        // Arrange
        PostMetadata postMetadata = PostMetadata.builder()
                .id(postId)
                .userId(authorId)
                .likesCount(initialLikesCount)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(postMetadata));
        
        // First like - not yet liked
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false);
        when(likeRepository.save(any(Like.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act - First like
        likeService.likePost(postId, userId);
        int countAfterFirstLike = postMetadata.getLikesCount();
        
        // Arrange - Second like attempt (already liked)
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(true);
        
        // Act - Second like (should be idempotent)
        likeService.likePost(postId, userId);
        
        // Assert - Counter should not change on second like
        assertThat(postMetadata.getLikesCount()).isEqualTo(countAfterFirstLike);
        assertThat(postMetadata.getLikesCount()).isEqualTo(initialLikesCount + 1);
        
        // Verify save was only called once (for the first like)
        verify(likeRepository, times(1)).save(any(Like.class));
        verify(eventPublisher, times(1)).publishEvent(anyString(), any());
    }
    
    /**
     * Property 30: Interações publicam eventos
     * **Validates: Requirements 6.6**
     * 
     * Para qualquer interação (curtida), um evento deve ser publicado no message broker
     */
    @Property(tries = 100)
    void likePublishesEvent(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String postId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String authorId,
            @ForAll @IntRange(min = 0, max = 1000) int initialLikesCount
    ) {
        // Arrange
        PostMetadata postMetadata = PostMetadata.builder()
                .id(postId)
                .userId(authorId)
                .likesCount(initialLikesCount)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(postMetadata));
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false);
        when(likeRepository.save(any(Like.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        likeService.likePost(postId, userId);
        
        // Assert - Verify event was published
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LikeCreatedEvent> eventCaptor = ArgumentCaptor.forClass(LikeCreatedEvent.class);
        
        verify(eventPublisher, times(1)).publishEvent(eventTypeCaptor.capture(), eventCaptor.capture());
        
        assertThat(eventTypeCaptor.getValue()).isEqualTo("like.created");
        
        LikeCreatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent).isNotNull();
        assertThat(publishedEvent.getPostId()).isEqualTo(postId);
        assertThat(publishedEvent.getUserId()).isEqualTo(userId);
        assertThat(publishedEvent.getPostAuthorId()).isEqualTo(authorId);
        assertThat(publishedEvent.getCreatedAt()).isNotNull();
    }
    
    /**
     * Additional property: Unlike is idempotent
     * Verifies that unliking a post multiple times has the same effect as unliking once
     */
    @Property(tries = 100)
    void unlikingIsIdempotent(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String postId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String authorId,
            @ForAll @IntRange(min = 1, max = 1000) int initialLikesCount
    ) {
        // Arrange
        PostMetadata postMetadata = PostMetadata.builder()
                .id(postId)
                .userId(authorId)
                .likesCount(initialLikesCount)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(postMetadata));
        
        // First unlike - like exists
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(true);
        doNothing().when(likeRepository).deleteByPostIdAndUserId(postId, userId);
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act - First unlike
        likeService.unlikePost(postId, userId);
        int countAfterFirstUnlike = postMetadata.getLikesCount();
        
        // Arrange - Second unlike attempt (like no longer exists)
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false);
        
        // Act - Second unlike (should be idempotent)
        likeService.unlikePost(postId, userId);
        
        // Assert - Counter should not change on second unlike
        assertThat(postMetadata.getLikesCount()).isEqualTo(countAfterFirstUnlike);
        assertThat(postMetadata.getLikesCount()).isEqualTo(initialLikesCount - 1);
        
        // Verify delete was only called once (for the first unlike)
        verify(likeRepository, times(1)).deleteByPostIdAndUserId(postId, userId);
    }
    
    /**
     * Additional property: Counter never goes below zero
     * Verifies that unliking when counter is 0 doesn't result in negative count
     */
    @Property(tries = 100)
    void counterNeverGoesBelowZero(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String postId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String authorId
    ) {
        // Arrange - Post with 0 likes
        PostMetadata postMetadata = PostMetadata.builder()
                .id(postId)
                .userId(authorId)
                .likesCount(0)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(postMetadata));
        when(likeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(true);
        doNothing().when(likeRepository).deleteByPostIdAndUserId(postId, userId);
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        likeService.unlikePost(postId, userId);
        
        // Assert - Counter should remain at 0, not go negative
        assertThat(postMetadata.getLikesCount()).isEqualTo(0);
        assertThat(postMetadata.getLikesCount()).isGreaterThanOrEqualTo(0);
    }
}
