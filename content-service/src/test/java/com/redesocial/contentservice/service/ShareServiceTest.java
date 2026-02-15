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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ShareService
 */
class ShareServiceTest {
    
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
    
    @Test
    void sharePost_whenOriginalPostNotFound_shouldThrowException() {
        // Arrange
        String postId = "nonexistent";
        String userId = "user123";
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThatThrownBy(() -> shareService.sharePost(postId, userId))
                .isInstanceOf(PostNotFoundException.class)
                .hasMessageContaining("Post not found");
    }
    
    @Test
    void sharePost_whenOriginalPostDeleted_shouldThrowException() {
        // Arrange
        String postId = "post123";
        String userId = "user456";
        Post originalPost = Post.builder()
                .id(postId)
                .userId("author789")
                .content("Original content")
                .hashtags(new ArrayList<>())
                .mediaUrls(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(postRepository.findById(postId)).thenReturn(Optional.of(originalPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThatThrownBy(() -> shareService.sharePost(postId, userId))
                .isInstanceOf(PostNotFoundException.class)
                .hasMessageContaining("Post not found or deleted");
    }
    
    @Test
    void sharePost_shouldCreateNewPostWithReference() {
        // Arrange
        String originalPostId = "post123";
        String userId = "user456";
        String authorId = "author789";
        
        Post originalPost = Post.builder()
                .id(originalPostId)
                .userId(authorId)
                .content("Original content")
                .hashtags(new ArrayList<>())
                .mediaUrls(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        PostMetadata originalMetadata = PostMetadata.builder()
                .id(originalPostId)
                .userId(authorId)
                .type(PostMetadata.PostType.TEXT)
                .likesCount(10)
                .commentsCount(5)
                .sharesCount(2)
                .isDeleted(false)
                .build();
        
        when(postRepository.findById(originalPostId)).thenReturn(Optional.of(originalPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(originalPostId)).thenReturn(Optional.of(originalMetadata));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        PostResponse response = shareService.sharePost(originalPostId, userId);
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getContent()).contains("Shared:");
        assertThat(response.getContent()).contains("Original content");
        assertThat(response.getLikesCount()).isEqualTo(0);
        assertThat(response.getCommentsCount()).isEqualTo(0);
        assertThat(response.getSharesCount()).isEqualTo(0);
        
        verify(postRepository, times(2)).save(any(Post.class)); // Original post + shared post
        verify(postMetadataRepository, times(2)).save(any(PostMetadata.class)); // Original metadata + shared metadata
        verify(shareRepository).save(any(Share.class));
    }
    
    @Test
    void sharePost_shouldIncrementSharesCount() {
        // Arrange
        String originalPostId = "post123";
        String userId = "user456";
        
        Post originalPost = Post.builder()
                .id(originalPostId)
                .userId("author789")
                .content("Original content")
                .hashtags(new ArrayList<>())
                .mediaUrls(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        PostMetadata originalMetadata = PostMetadata.builder()
                .id(originalPostId)
                .userId("author789")
                .type(PostMetadata.PostType.TEXT)
                .sharesCount(5)
                .build();
        
        when(postRepository.findById(originalPostId)).thenReturn(Optional.of(originalPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(originalPostId)).thenReturn(Optional.of(originalMetadata));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        shareService.sharePost(originalPostId, userId);
        
        // Assert
        assertThat(originalMetadata.getSharesCount()).isEqualTo(6);
        verify(postMetadataRepository, times(2)).save(any(PostMetadata.class));
    }
    
    @Test
    void sharePost_shouldPublishShareCreatedEvent() {
        // Arrange
        String originalPostId = "post123";
        String userId = "user456";
        String authorId = "author789";
        
        Post originalPost = Post.builder()
                .id(originalPostId)
                .userId(authorId)
                .content("Original content")
                .hashtags(new ArrayList<>())
                .mediaUrls(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        PostMetadata originalMetadata = PostMetadata.builder()
                .id(originalPostId)
                .userId(authorId)
                .type(PostMetadata.PostType.TEXT)
                .sharesCount(0)
                .build();
        
        when(postRepository.findById(originalPostId)).thenReturn(Optional.of(originalPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(originalPostId)).thenReturn(Optional.of(originalMetadata));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        PostResponse response = shareService.sharePost(originalPostId, userId);
        
        // Assert
        ArgumentCaptor<ShareCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ShareCreatedEvent.class);
        verify(eventPublisher).publishEvent(eq("share.created"), eventCaptor.capture());
        
        ShareCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getOriginalPostId()).isEqualTo(originalPostId);
        assertThat(event.getSharedPostId()).isEqualTo(response.getId());
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getOriginalAuthorId()).isEqualTo(authorId);
        assertThat(event.getCreatedAt()).isNotNull();
    }
    
    @Test
    void sharePost_shouldCreateShareRecord() {
        // Arrange
        String originalPostId = "post123";
        String userId = "user456";
        
        Post originalPost = Post.builder()
                .id(originalPostId)
                .userId("author789")
                .content("Original content")
                .hashtags(new ArrayList<>())
                .mediaUrls(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        PostMetadata originalMetadata = PostMetadata.builder()
                .id(originalPostId)
                .userId("author789")
                .type(PostMetadata.PostType.TEXT)
                .sharesCount(0)
                .build();
        
        when(postRepository.findById(originalPostId)).thenReturn(Optional.of(originalPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(originalPostId)).thenReturn(Optional.of(originalMetadata));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        PostResponse response = shareService.sharePost(originalPostId, userId);
        
        // Assert
        ArgumentCaptor<Share> shareCaptor = ArgumentCaptor.forClass(Share.class);
        verify(shareRepository).save(shareCaptor.capture());
        
        Share share = shareCaptor.getValue();
        assertThat(share.getOriginalPostId()).isEqualTo(originalPostId);
        assertThat(share.getSharedPostId()).isEqualTo(response.getId());
        assertThat(share.getUserId()).isEqualTo(userId);
    }
    
    @Test
    void sharePost_shouldCopyMediaUrlsAndHashtags() {
        // Arrange
        String originalPostId = "post123";
        String userId = "user456";
        
        Post.MediaUrl mediaUrl = Post.MediaUrl.builder()
                .url("https://example.com/image.jpg")
                .type(Post.MediaType.IMAGE)
                .thumbnailUrl("https://example.com/thumb.jpg")
                .build();
        
        Post originalPost = Post.builder()
                .id(originalPostId)
                .userId("author789")
                .content("Original content #test #share")
                .hashtags(java.util.Arrays.asList("test", "share"))
                .mediaUrls(java.util.Arrays.asList(mediaUrl))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        PostMetadata originalMetadata = PostMetadata.builder()
                .id(originalPostId)
                .userId("author789")
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
        PostResponse response = shareService.sharePost(originalPostId, userId);
        
        // Assert
        assertThat(response.getMediaUrls()).hasSize(1);
        assertThat(response.getHashtags()).containsExactly("test", "share");
    }
    
    @Test
    void sharePost_multipleShares_shouldIncrementCounterCorrectly() {
        // Arrange
        String originalPostId = "post123";
        
        Post originalPost = Post.builder()
                .id(originalPostId)
                .userId("author789")
                .content("Original content")
                .hashtags(new ArrayList<>())
                .mediaUrls(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        PostMetadata originalMetadata = PostMetadata.builder()
                .id(originalPostId)
                .userId("author789")
                .type(PostMetadata.PostType.TEXT)
                .sharesCount(0)
                .build();
        
        when(postRepository.findById(originalPostId)).thenReturn(Optional.of(originalPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(originalPostId)).thenReturn(Optional.of(originalMetadata));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        // Act
        shareService.sharePost(originalPostId, "user1");
        shareService.sharePost(originalPostId, "user2");
        shareService.sharePost(originalPostId, "user3");
        
        // Assert
        assertThat(originalMetadata.getSharesCount()).isEqualTo(3);
        verify(shareRepository, times(3)).save(any(Share.class));
        verify(eventPublisher, times(3)).publishEvent(eq("share.created"), any(ShareCreatedEvent.class));
    }
}
