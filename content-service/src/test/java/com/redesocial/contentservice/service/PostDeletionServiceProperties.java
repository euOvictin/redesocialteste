package com.redesocial.contentservice.service;

import com.redesocial.contentservice.event.PostDeletedEvent;
import com.redesocial.contentservice.exception.PostNotFoundException;
import com.redesocial.contentservice.exception.UnauthorizedAccessException;
import com.redesocial.contentservice.model.jpa.PostMetadata;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for PostDeletionService
 * Feature: rede-social-brasileira
 */
class PostDeletionServiceProperties {
    
    private PostMetadataRepository postMetadataRepository;
    private EventPublisher eventPublisher;
    private PostDeletionService postDeletionService;
    
    @BeforeEach
    void setUp() {
        postMetadataRepository = Mockito.mock(PostMetadataRepository.class);
        eventPublisher = Mockito.mock(EventPublisher.class);
        postDeletionService = new PostDeletionService(postMetadataRepository, eventPublisher);
    }
    
    /**
     * Property 12: Exclusão de post remove do feed
     * **Validates: Requirements 2.7**
     * 
     * Para qualquer post existente, solicitar exclusão deve marcar como deletado 
     * e remover de todos os feeds (através do evento publicado)
     */
    @Property(tries = 100)
    void deletingExistingPostMarksAsDeletedAndPublishesEvent(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String postId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId,
            @ForAll("postTypes") PostMetadata.PostType postType,
            @ForAll @IntRange(min = 0, max = 10000) int likesCount,
            @ForAll @IntRange(min = 0, max = 1000) int commentsCount,
            @ForAll @IntRange(min = 0, max = 500) int sharesCount
    ) {
        // Arrange - Create an existing post that is not deleted
        PostMetadata existingPost = PostMetadata.builder()
                .id(postId)
                .userId(userId)
                .type(postType)
                .likesCount(likesCount)
                .commentsCount(commentsCount)
                .sharesCount(sharesCount)
                .isDeleted(false)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(existingPost));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act - Delete the post
        postDeletionService.deletePost(postId, userId);
        
        // Assert - Post is marked as deleted
        ArgumentCaptor<PostMetadata> metadataCaptor = ArgumentCaptor.forClass(PostMetadata.class);
        verify(postMetadataRepository, times(1)).save(metadataCaptor.capture());
        
        PostMetadata savedPost = metadataCaptor.getValue();
        assertThat(savedPost.getIsDeleted()).isTrue();
        assertThat(savedPost.getId()).isEqualTo(postId);
        assertThat(savedPost.getUserId()).isEqualTo(userId);
        
        // Assert - Event is published to remove from feeds
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PostDeletedEvent> eventCaptor = ArgumentCaptor.forClass(PostDeletedEvent.class);
        
        verify(eventPublisher, times(1)).publishEvent(eventTypeCaptor.capture(), eventCaptor.capture());
        
        assertThat(eventTypeCaptor.getValue()).isEqualTo("post.deleted");
        
        PostDeletedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent).isNotNull();
        assertThat(publishedEvent.getPostId()).isEqualTo(postId);
        assertThat(publishedEvent.getUserId()).isEqualTo(userId);
        assertThat(publishedEvent.getDeletedAt()).isNotNull();
    }
    
    /**
     * Additional property: Deleting non-existent post throws exception
     * Verifies that attempting to delete a post that doesn't exist fails appropriately
     */
    @Property(tries = 100)
    void deletingNonExistentPostThrowsException(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String postId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId
    ) {
        // Arrange - Post does not exist
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.empty());
        
        // Act & Assert - Should throw PostNotFoundException
        assertThatThrownBy(() -> postDeletionService.deletePost(postId, userId))
                .isInstanceOf(PostNotFoundException.class);
        
        // Verify no save or event publication occurred
        verify(postMetadataRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(), any());
    }
    
    /**
     * Additional property: Unauthorized user cannot delete post
     * Verifies that a user cannot delete another user's post
     */
    @Property(tries = 100)
    void unauthorizedUserCannotDeletePost(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String postId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String ownerId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String unauthorizedUserId
    ) {
        Assume.that(!ownerId.equals(unauthorizedUserId));
        
        // Arrange - Post exists but belongs to different user
        PostMetadata existingPost = PostMetadata.builder()
                .id(postId)
                .userId(ownerId)
                .type(PostMetadata.PostType.TEXT)
                .isDeleted(false)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(existingPost));
        
        // Act & Assert - Should throw UnauthorizedAccessException
        assertThatThrownBy(() -> postDeletionService.deletePost(postId, unauthorizedUserId))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("not authorized");
        
        // Verify no save or event publication occurred
        verify(postMetadataRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(), any());
    }
    
    /**
     * Additional property: Deleting already deleted post is idempotent
     * Verifies that deleting an already deleted post still marks it as deleted and publishes event
     */
    @Property(tries = 100)
    void deletingAlreadyDeletedPostIsIdempotent(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String postId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId
    ) {
        // Arrange - Post already deleted
        PostMetadata alreadyDeletedPost = PostMetadata.builder()
                .id(postId)
                .userId(userId)
                .type(PostMetadata.PostType.TEXT)
                .isDeleted(true)
                .build();
        
        when(postMetadataRepository.findById(postId)).thenReturn(Optional.of(alreadyDeletedPost));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act - Delete the already deleted post
        postDeletionService.deletePost(postId, userId);
        
        // Assert - Post remains marked as deleted
        ArgumentCaptor<PostMetadata> metadataCaptor = ArgumentCaptor.forClass(PostMetadata.class);
        verify(postMetadataRepository, times(1)).save(metadataCaptor.capture());
        
        PostMetadata savedPost = metadataCaptor.getValue();
        assertThat(savedPost.getIsDeleted()).isTrue();
        
        // Assert - Event is still published
        verify(eventPublisher, times(1)).publishEvent(eq("post.deleted"), any(PostDeletedEvent.class));
    }
    
    /**
     * Arbitrary provider for PostType enum
     */
    @Provide
    Arbitrary<PostMetadata.PostType> postTypes() {
        return Arbitraries.of(PostMetadata.PostType.values());
    }
}
