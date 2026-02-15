package com.redesocial.contentservice.service;

import com.redesocial.contentservice.dto.PostResponse;
import com.redesocial.contentservice.event.ShareCreatedEvent;
import com.redesocial.contentservice.model.jpa.PostMetadata;
import com.redesocial.contentservice.model.jpa.Share;
import com.redesocial.contentservice.model.mongo.Post;
import com.redesocial.contentservice.repository.jpa.PostMetadataRepository;
import com.redesocial.contentservice.repository.jpa.ShareRepository;
import com.redesocial.contentservice.repository.mongo.PostRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for ShareService
 * Feature: rede-social-brasileira
 */
class ShareServiceProperties {
    
    private PostRepository postRepository;
    private PostMetadataRepository postMetadataRepository;
    private ShareRepository shareRepository;
    private EventPublisher eventPublisher;
    private ShareService shareService;
    
    @BeforeEach
    void setUp() {
        postRepository = Mockito.mock(PostRepository.class);
        postMetadataRepository = Mockito.mock(PostMetadataRepository.class);
        shareRepository = Mockito.mock(ShareRepository.class);
        eventPublisher = Mockito.mock(EventPublisher.class);
        shareService = new ShareService(postRepository, postMetadataRepository, shareRepository, eventPublisher);
    }
    
    /**
     * Property 29: Compartilhar cria post com referência
     * **Validates: Requirements 6.5**
     * 
     * Para qualquer post compartilhado, um novo post deve ser criado contendo referência ao post original
     */
    @Property(tries = 100)
    void sharingCreatesPostWithReference(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String originalPostId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String authorId,
            @ForAll @StringLength(min = 1, max = 500) String content,
            @ForAll @IntRange(min = 0, max = 100) int initialSharesCount
    ) {
        // Arrange
        Post originalPost = Post.builder()
                .id(originalPostId)
                .userId(authorId)
                .content(content)
                .hashtags(new ArrayList<>())
                .mediaUrls(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        PostMetadata originalMetadata = PostMetadata.builder()
                .id(originalPostId)
                .userId(authorId)
                .type(PostMetadata.PostType.TEXT)
                .sharesCount(initialSharesCount)
                .build();
        
        when(postRepository.findById(originalPostId)).thenReturn(Optional.of(originalPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(originalPostId)).thenReturn(Optional.of(originalMetadata));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        PostResponse sharedPost = shareService.sharePost(originalPostId, userId);
        
        // Assert - New post is created
        assertThat(sharedPost).isNotNull();
        assertThat(sharedPost.getId()).isNotNull();
        assertThat(sharedPost.getId()).isNotEqualTo(originalPostId);
        assertThat(sharedPost.getUserId()).isEqualTo(userId);
        assertThat(sharedPost.getContent()).contains("Shared:");
        assertThat(sharedPost.getContent()).contains(content);
        
        // Verify Share record was created linking original to shared post
        ArgumentCaptor<Share> shareCaptor = ArgumentCaptor.forClass(Share.class);
        verify(shareRepository, times(1)).save(shareCaptor.capture());
        
        Share share = shareCaptor.getValue();
        assertThat(share.getOriginalPostId()).isEqualTo(originalPostId);
        assertThat(share.getSharedPostId()).isEqualTo(sharedPost.getId());
        assertThat(share.getUserId()).isEqualTo(userId);
    }
    
    /**
     * Property: Sharing increments shares count
     * **Validates: Requirements 6.5**
     * 
     * Para qualquer post compartilhado, o contador shares_count do post original deve ser incrementado em 1
     */
    @Property(tries = 100)
    void sharingIncrementsSharesCount(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String originalPostId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String authorId,
            @ForAll @StringLength(min = 1, max = 500) String content,
            @ForAll @IntRange(min = 0, max = 1000) int initialSharesCount
    ) {
        // Arrange
        Post originalPost = Post.builder()
                .id(originalPostId)
                .userId(authorId)
                .content(content)
                .hashtags(new ArrayList<>())
                .mediaUrls(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        PostMetadata originalMetadata = PostMetadata.builder()
                .id(originalPostId)
                .userId(authorId)
                .type(PostMetadata.PostType.TEXT)
                .sharesCount(initialSharesCount)
                .build();
        
        when(postRepository.findById(originalPostId)).thenReturn(Optional.of(originalPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(originalPostId)).thenReturn(Optional.of(originalMetadata));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        shareService.sharePost(originalPostId, userId);
        
        // Assert - Shares count incremented by exactly 1
        assertThat(originalMetadata.getSharesCount()).isEqualTo(initialSharesCount + 1);
        verify(postMetadataRepository, times(2)).save(any(PostMetadata.class)); // Original + shared post metadata
    }
    
    /**
     * Property: Sharing publishes event
     * **Validates: Requirements 6.6**
     * 
     * Para qualquer compartilhamento, um evento share.created deve ser publicado no message broker
     */
    @Property(tries = 100)
    void sharingPublishesEvent(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String originalPostId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String authorId,
            @ForAll @StringLength(min = 1, max = 500) String content,
            @ForAll @IntRange(min = 0, max = 100) int initialSharesCount
    ) {
        // Arrange
        Post originalPost = Post.builder()
                .id(originalPostId)
                .userId(authorId)
                .content(content)
                .hashtags(new ArrayList<>())
                .mediaUrls(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        PostMetadata originalMetadata = PostMetadata.builder()
                .id(originalPostId)
                .userId(authorId)
                .type(PostMetadata.PostType.TEXT)
                .sharesCount(initialSharesCount)
                .build();
        
        when(postRepository.findById(originalPostId)).thenReturn(Optional.of(originalPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(originalPostId)).thenReturn(Optional.of(originalMetadata));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        PostResponse sharedPost = shareService.sharePost(originalPostId, userId);
        
        // Assert - Verify event was published
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ShareCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ShareCreatedEvent.class);
        
        verify(eventPublisher, times(1)).publishEvent(eventTypeCaptor.capture(), eventCaptor.capture());
        
        assertThat(eventTypeCaptor.getValue()).isEqualTo("share.created");
        
        ShareCreatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent).isNotNull();
        assertThat(publishedEvent.getOriginalPostId()).isEqualTo(originalPostId);
        assertThat(publishedEvent.getSharedPostId()).isEqualTo(sharedPost.getId());
        assertThat(publishedEvent.getUserId()).isEqualTo(userId);
        assertThat(publishedEvent.getOriginalAuthorId()).isEqualTo(authorId);
        assertThat(publishedEvent.getCreatedAt()).isNotNull();
    }
    
    /**
     * Property: Shared post has zero counters
     * Verifies that the newly created shared post starts with zero likes, comments, and shares
     */
    @Property(tries = 100)
    void sharedPostHasZeroCounters(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String originalPostId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String authorId,
            @ForAll @StringLength(min = 1, max = 500) String content,
            @ForAll @IntRange(min = 0, max = 100) int originalLikes,
            @ForAll @IntRange(min = 0, max = 100) int originalComments,
            @ForAll @IntRange(min = 0, max = 100) int originalShares
    ) {
        // Arrange
        Post originalPost = Post.builder()
                .id(originalPostId)
                .userId(authorId)
                .content(content)
                .hashtags(new ArrayList<>())
                .mediaUrls(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        PostMetadata originalMetadata = PostMetadata.builder()
                .id(originalPostId)
                .userId(authorId)
                .type(PostMetadata.PostType.TEXT)
                .likesCount(originalLikes)
                .commentsCount(originalComments)
                .sharesCount(originalShares)
                .build();
        
        when(postRepository.findById(originalPostId)).thenReturn(Optional.of(originalPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(originalPostId)).thenReturn(Optional.of(originalMetadata));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        PostResponse sharedPost = shareService.sharePost(originalPostId, userId);
        
        // Assert - Shared post starts with zero counters
        assertThat(sharedPost.getLikesCount()).isEqualTo(0);
        assertThat(sharedPost.getCommentsCount()).isEqualTo(0);
        assertThat(sharedPost.getSharesCount()).isEqualTo(0);
    }
    
    /**
     * Property: Multiple shares increment counter correctly
     * Verifies that sharing the same post multiple times increments the counter correctly
     */
    @Property(tries = 100)
    void multipleSharesIncrementCounterCorrectly(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String originalPostId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String authorId,
            @ForAll @StringLength(min = 1, max = 500) String content,
            @ForAll @IntRange(min = 0, max = 100) int initialSharesCount,
            @ForAll @IntRange(min = 1, max = 5) int numberOfShares
    ) {
        // Arrange
        Post originalPost = Post.builder()
                .id(originalPostId)
                .userId(authorId)
                .content(content)
                .hashtags(new ArrayList<>())
                .mediaUrls(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        PostMetadata originalMetadata = PostMetadata.builder()
                .id(originalPostId)
                .userId(authorId)
                .type(PostMetadata.PostType.TEXT)
                .sharesCount(initialSharesCount)
                .build();
        
        when(postRepository.findById(originalPostId)).thenReturn(Optional.of(originalPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(originalPostId)).thenReturn(Optional.of(originalMetadata));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act - Share the post multiple times
        for (int i = 0; i < numberOfShares; i++) {
            shareService.sharePost(originalPostId, "user" + i);
        }
        
        // Assert - Shares count incremented by exact number of shares
        assertThat(originalMetadata.getSharesCount()).isEqualTo(initialSharesCount + numberOfShares);
        verify(shareRepository, times(numberOfShares)).save(any(Share.class));
        verify(eventPublisher, times(numberOfShares)).publishEvent(eq("share.created"), any(ShareCreatedEvent.class));
    }
    
    /**
     * Property: Shared post copies media and hashtags
     * Verifies that the shared post contains the same media URLs and hashtags as the original
     */
    @Property(tries = 100)
    void sharedPostCopiesMediaAndHashtags(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String originalPostId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String authorId,
            @ForAll @StringLength(min = 1, max = 500) String content
    ) {
        // Arrange
        Post.MediaUrl mediaUrl = Post.MediaUrl.builder()
                .url("https://example.com/image.jpg")
                .type(Post.MediaType.IMAGE)
                .thumbnailUrl("https://example.com/thumb.jpg")
                .build();
        
        Post originalPost = Post.builder()
                .id(originalPostId)
                .userId(authorId)
                .content(content)
                .hashtags(java.util.Arrays.asList("test", "share"))
                .mediaUrls(java.util.Arrays.asList(mediaUrl))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        PostMetadata originalMetadata = PostMetadata.builder()
                .id(originalPostId)
                .userId(authorId)
                .type(PostMetadata.PostType.IMAGE)
                .sharesCount(0)
                .build();
        
        when(postRepository.findById(originalPostId)).thenReturn(Optional.of(originalPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(originalPostId)).thenReturn(Optional.of(originalMetadata));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        PostResponse sharedPost = shareService.sharePost(originalPostId, userId);
        
        // Assert - Shared post has same media and hashtags
        assertThat(sharedPost.getMediaUrls()).hasSize(1);
        assertThat(sharedPost.getHashtags()).containsExactly("test", "share");
    }
}
