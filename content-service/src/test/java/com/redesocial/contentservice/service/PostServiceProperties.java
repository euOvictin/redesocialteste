package com.redesocial.contentservice.service;

import com.redesocial.contentservice.dto.CreatePostRequest;
import com.redesocial.contentservice.dto.PostResponse;
import com.redesocial.contentservice.event.PostCreatedEvent;
import com.redesocial.contentservice.model.jpa.PostMetadata;
import com.redesocial.contentservice.model.mongo.Post;
import com.redesocial.contentservice.repository.jpa.PostMetadataRepository;
import com.redesocial.contentservice.repository.mongo.PostRepository;
import com.redesocial.contentservice.util.HashtagExtractor;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for PostService
 * Feature: rede-social-brasileira
 */
class PostServiceProperties {
    
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
    
    /**
     * Property 6: Post com texto válido é criado
     * **Validates: Requirements 2.1**
     * 
     * Para qualquer texto entre 1 e 5000 caracteres, criar post deve retornar ID único 
     * e o post deve ser recuperável
     */
    @Property(tries = 100)
    void postWithValidTextIsCreated(
            @ForAll @StringLength(min = 1, max = 5000) String content,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId
    ) {
        // Arrange
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
        assertThat(response.getId()).isNotNull().isNotEmpty();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getContent()).isEqualTo(content);
        assertThat(response.getLikesCount()).isEqualTo(0);
        assertThat(response.getCommentsCount()).isEqualTo(0);
        assertThat(response.getSharesCount()).isEqualTo(0);
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
        
        // Verify post was saved to MongoDB
        verify(postRepository, times(1)).save(any(Post.class));
        
        // Verify metadata was saved to PostgreSQL
        verify(postMetadataRepository, times(1)).save(any(PostMetadata.class));
    }
    
    /**
     * Property 10: Hashtags são extraídas automaticamente
     * **Validates: Requirements 2.5**
     * 
     * Para qualquer post contendo padrão #palavra, todas as hashtags devem ser 
     * extraídas e indexadas automaticamente
     */
    @Property(tries = 100)
    void hashtagsAreExtractedAutomatically(
            @ForAll @AlphaChars @StringLength(min = 3, max = 20) String hashtag1,
            @ForAll @AlphaChars @StringLength(min = 3, max = 20) String hashtag2,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId
    ) {
        // Arrange
        String content = String.format("This is a post with #%s and #%s hashtags", hashtag1, hashtag2);
        
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
        assertThat(response.getHashtags()).isNotNull();
        assertThat(response.getHashtags()).contains(hashtag1.toLowerCase(), hashtag2.toLowerCase());
        
        // Verify hashtags were extracted correctly
        List<String> expectedHashtags = HashtagExtractor.extractHashtags(content);
        assertThat(response.getHashtags()).containsExactlyInAnyOrderElementsOf(expectedHashtags);
    }
    
    /**
     * Property 11: Criação de post publica evento
     * **Validates: Requirements 2.6**
     * 
     * Para qualquer post criado, um evento deve ser publicado no message broker 
     * para processamento assíncrono
     */
    @Property(tries = 100)
    void postCreationPublishesEvent(
            @ForAll @StringLength(min = 1, max = 5000) String content,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId
    ) {
        // Arrange
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        CreatePostRequest request = CreatePostRequest.builder()
                .content(content)
                .userId(userId)
                .build();
        
        // Act
        postService.createPost(request);
        
        // Assert - Verify event was published
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PostCreatedEvent> eventCaptor = ArgumentCaptor.forClass(PostCreatedEvent.class);
        
        verify(eventPublisher, times(1)).publishEvent(eventTypeCaptor.capture(), eventCaptor.capture());
        
        assertThat(eventTypeCaptor.getValue()).isEqualTo("post.created");
        
        PostCreatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent).isNotNull();
        assertThat(publishedEvent.getPostId()).isNotNull();
        assertThat(publishedEvent.getUserId()).isEqualTo(userId);
        assertThat(publishedEvent.getContent()).isEqualTo(content);
        assertThat(publishedEvent.getType()).isEqualTo("TEXT");
        assertThat(publishedEvent.getCreatedAt()).isNotNull();
    }
    
    /**
     * Additional property: Post can be retrieved after creation
     * Verifies that a created post can be fetched by its ID
     */
    @Property(tries = 100)
    void createdPostCanBeRetrieved(
            @ForAll @StringLength(min = 1, max = 5000) String content,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId
    ) {
        // Arrange - Create post
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishEvent(anyString(), any());
        
        CreatePostRequest request = CreatePostRequest.builder()
                .content(content)
                .userId(userId)
                .build();
        
        PostResponse createdPost = postService.createPost(request);
        
        // Arrange - Setup mocks for retrieval
        Post mockPost = Post.builder()
                .id(createdPost.getId())
                .userId(userId)
                .content(content)
                .hashtags(createdPost.getHashtags())
                .createdAt(createdPost.getCreatedAt())
                .updatedAt(createdPost.getUpdatedAt())
                .build();
        
        PostMetadata mockMetadata = PostMetadata.builder()
                .id(createdPost.getId())
                .userId(userId)
                .type(PostMetadata.PostType.TEXT)
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .isDeleted(false)
                .build();
        
        when(postRepository.findById(createdPost.getId())).thenReturn(Optional.of(mockPost));
        when(postMetadataRepository.findByIdAndIsDeletedFalse(createdPost.getId())).thenReturn(Optional.of(mockMetadata));
        
        // Act - Retrieve post
        PostResponse retrievedPost = postService.getPost(createdPost.getId());
        
        // Assert
        assertThat(retrievedPost).isNotNull();
        assertThat(retrievedPost.getId()).isEqualTo(createdPost.getId());
        assertThat(retrievedPost.getUserId()).isEqualTo(userId);
        assertThat(retrievedPost.getContent()).isEqualTo(content);
    }
}
