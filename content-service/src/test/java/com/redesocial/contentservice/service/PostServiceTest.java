package com.redesocial.contentservice.service;

import com.redesocial.contentservice.dto.CreatePostRequest;
import com.redesocial.contentservice.dto.PostResponse;
import com.redesocial.contentservice.model.jpa.PostMetadata;
import com.redesocial.contentservice.model.mongo.Post;
import com.redesocial.contentservice.repository.jpa.PostMetadataRepository;
import com.redesocial.contentservice.repository.mongo.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PostService
 */
class PostServiceTest {
    
    private PostRepository postRepository;
    private PostMetadataRepository postMetadataRepository;
    private EventPublisher eventPublisher;
    private PostService postService;
    
    @BeforeEach
    void setUp() {
        postRepository = Mockito.mock(PostRepository.class);
        postMetadataRepository = Mockito.mock(PostMetadataRepository.class);
        eventPublisher = Mockito.mock(EventPublisher.class);
        postService = new PostService(postRepository, postMetadataRepository, eventPublisher);
    }
    
    @Test
    void createPost_withMinimumLength_shouldSucceed() {
        // Arrange
        String content = "a"; // minimum 1 character
        String userId = "user123";
        
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        CreatePostRequest request = CreatePostRequest.builder()
                .content(content)
                .userId(userId)
                .build();
        
        // Act
        PostResponse response = postService.createPost(request);
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo(content);
    }
    
    @Test
    void createPost_withMaximumLength_shouldSucceed() {
        // Arrange
        String content = "a".repeat(5000); // maximum 5000 characters
        String userId = "user123";
        
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        CreatePostRequest request = CreatePostRequest.builder()
                .content(content)
                .userId(userId)
                .build();
        
        // Act
        PostResponse response = postService.createPost(request);
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(5000);
    }
    
    @Test
    void createPost_withNoHashtags_shouldSucceed() {
        // Arrange
        String content = "This is a post without hashtags";
        String userId = "user123";
        
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        CreatePostRequest request = CreatePostRequest.builder()
                .content(content)
                .userId(userId)
                .build();
        
        // Act
        PostResponse response = postService.createPost(request);
        
        // Assert
        assertThat(response.getHashtags()).isEmpty();
    }
    
    @Test
    void createPost_withMultipleHashtags_shouldExtractAll() {
        // Arrange
        String content = "Post with #java #spring #boot #testing hashtags";
        String userId = "user123";
        
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        CreatePostRequest request = CreatePostRequest.builder()
                .content(content)
                .userId(userId)
                .build();
        
        // Act
        PostResponse response = postService.createPost(request);
        
        // Assert
        assertThat(response.getHashtags()).containsExactlyInAnyOrder("java", "spring", "boot", "testing");
    }
    
    @Test
    void createPost_withDuplicateHashtags_shouldDeduplicateHashtags() {
        // Arrange
        String content = "Post with #java #spring #java #spring duplicate hashtags";
        String userId = "user123";
        
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        CreatePostRequest request = CreatePostRequest.builder()
                .content(content)
                .userId(userId)
                .build();
        
        // Act
        PostResponse response = postService.createPost(request);
        
        // Assert
        assertThat(response.getHashtags()).containsExactlyInAnyOrder("java", "spring", "duplicate", "hashtags");
        assertThat(response.getHashtags()).hasSize(4); // No duplicates
    }
    
    @Test
    void getPost_whenPostNotFound_shouldThrowException() {
        // Arrange
        String postId = "nonexistent";
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThatThrownBy(() -> postService.getPost(postId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Post not found");
    }
    
    @Test
    void getPost_whenPostDeleted_shouldThrowException() {
        // Arrange
        String postId = "deleted-post";
        Post post = Post.builder()
                .id(postId)
                .userId("user123")
                .content("Deleted post")
                .build();
        
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThatThrownBy(() -> postService.getPost(postId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Post not found or deleted");
    }
}
