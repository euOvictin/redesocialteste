package com.redesocial.contentservice.service;

import com.redesocial.contentservice.repository.mongo.StoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

class StoryCleanupServiceTest {
    
    @Mock
    private StoryRepository storyRepository;
    
    private StoryCleanupService storyCleanupService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        storyCleanupService = new StoryCleanupService(storyRepository);
    }
    
    @Test
    void cleanupExpiredStories_executesSuccessfully() {
        // Arrange
        when(storyRepository.count()).thenReturn(10L);
        
        // Act
        storyCleanupService.cleanupExpiredStories();
        
        // Assert
        verify(storyRepository, times(1)).count();
    }
    
    @Test
    void cleanupExpiredStories_handlesExceptionGracefully() {
        // Arrange
        when(storyRepository.count()).thenThrow(new RuntimeException("Database error"));
        
        // Act - should not throw exception
        storyCleanupService.cleanupExpiredStories();
        
        // Assert
        verify(storyRepository, times(1)).count();
    }
}
