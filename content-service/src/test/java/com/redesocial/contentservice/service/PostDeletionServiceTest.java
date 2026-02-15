package com.redesocial.contentservice.service;

import com.redesocial.contentservice.event.PostDeletedEvent;
import com.redesocial.contentservice.exception.PostNotFoundException;
import com.redesocial.contentservice.exception.UnauthorizedAccessException;
import com.redesocial.contentservice.model.jpa.PostMetadata;
import com.redesocial.contentservice.repository.jpa.PostMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostDeletionServiceTest {
    
    @Mock
    private PostMetadataRepository postMetadataRepository;
    
    @Mock
    private EventPublisher eventPublisher;
    
    @InjectMocks
    private PostDeletionService postDeletionService;
    
    private PostMetadata testPost;
    
    @BeforeEach
    void setUp() {
        testPost = PostMetadata.builder()
                .id("post123")
                .userId("user123")
                .type(PostMetadata.PostType.TEXT)
                .isDeleted(false)
                .build();
    }
    
    @Test
    void deletePost_ValidRequest_MarksAsDeleted() {
        when(postMetadataRepository.findById("post123")).thenReturn(Optional.of(testPost));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenReturn(testPost);
        
        postDeletionService.deletePost("post123", "user123");
        
        ArgumentCaptor<PostMetadata> captor = ArgumentCaptor.forClass(PostMetadata.class);
        verify(postMetadataRepository).save(captor.capture());
        
        PostMetadata savedPost = captor.getValue();
        assertTrue(savedPost.getIsDeleted(), "Post should be marked as deleted");
    }
    
    @Test
    void deletePost_ValidRequest_PublishesEvent() {
        when(postMetadataRepository.findById("post123")).thenReturn(Optional.of(testPost));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenReturn(testPost);
        
        postDeletionService.deletePost("post123", "user123");
        
        ArgumentCaptor<PostDeletedEvent> eventCaptor = ArgumentCaptor.forClass(PostDeletedEvent.class);
        verify(eventPublisher).publishEvent(eq("post.deleted"), eventCaptor.capture());
        
        PostDeletedEvent event = eventCaptor.getValue();
        assertEquals("post123", event.getPostId());
        assertEquals("user123", event.getUserId());
        assertNotNull(event.getDeletedAt());
    }
    
    @Test
    void deletePost_PostNotFound_ThrowsException() {
        when(postMetadataRepository.findById("nonexistent")).thenReturn(Optional.empty());
        
        assertThrows(PostNotFoundException.class, () -> {
            postDeletionService.deletePost("nonexistent", "user123");
        });
        
        verify(postMetadataRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(), any());
    }
    
    @Test
    void deletePost_UnauthorizedUser_ThrowsException() {
        when(postMetadataRepository.findById("post123")).thenReturn(Optional.of(testPost));
        
        assertThrows(UnauthorizedAccessException.class, () -> {
            postDeletionService.deletePost("post123", "wrongUser");
        });
        
        verify(postMetadataRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(), any());
    }
    
    @Test
    void deletePost_AlreadyDeleted_StillMarksAsDeleted() {
        testPost.setIsDeleted(true);
        when(postMetadataRepository.findById("post123")).thenReturn(Optional.of(testPost));
        when(postMetadataRepository.save(any(PostMetadata.class))).thenReturn(testPost);
        
        postDeletionService.deletePost("post123", "user123");
        
        verify(postMetadataRepository).save(any(PostMetadata.class));
        verify(eventPublisher).publishEvent(eq("post.deleted"), any(PostDeletedEvent.class));
    }
}
