package com.redesocial.contentservice.controller;

import com.redesocial.contentservice.exception.StoryNotFoundException;
import com.redesocial.contentservice.model.mongo.Post;
import com.redesocial.contentservice.model.mongo.Story;
import com.redesocial.contentservice.model.mongo.StoryView;
import com.redesocial.contentservice.service.MediaService;
import com.redesocial.contentservice.service.StoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StoryControllerTest {
    
    @Mock
    private StoryService storyService;
    
    @Mock
    private MediaService mediaService;
    
    private StoryController storyController;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        storyController = new StoryController(storyService, mediaService);
    }
    
    @Test
    void createStory_withValidImage_returnsCreatedStory() throws Exception {
        // Arrange
        String userId = "user123";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test image content".getBytes()
        );
        
        Map<String, String> uploadResult = Map.of(
                "url", "https://s3.example.com/story.jpg",
                "thumbnailUrl", "https://s3.example.com/story-thumb.jpg"
        );
        
        Story story = Story.builder()
                .id("story123")
                .userId(userId)
                .mediaUrl(Post.MediaUrl.builder()
                        .url("https://s3.example.com/story.jpg")
                        .type(Post.MediaType.IMAGE)
                        .thumbnailUrl("https://s3.example.com/story-thumb.jpg")
                        .build())
                .viewsCount(0)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .createdAt(LocalDateTime.now())
                .build();
        
        when(mediaService.uploadImageWithThumbnail(any(), eq(userId))).thenReturn(uploadResult);
        when(storyService.createStory(eq(userId), any())).thenReturn(story);
        
        // Act
        ResponseEntity<?> response = storyController.createStory(file, userId);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(mediaService, times(1)).uploadImageWithThumbnail(any(), eq(userId));
        verify(storyService, times(1)).createStory(eq(userId), any());
    }
    
    @Test
    void createStory_withValidVideo_returnsCreatedStory() throws Exception {
        // Arrange
        String userId = "user123";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.mp4",
                "video/mp4",
                "test video content".getBytes()
        );
        
        Map<String, String> uploadResult = Map.of(
                "url", "https://s3.example.com/story.mp4",
                "thumbnailUrl", "https://s3.example.com/story-thumb.jpg"
        );
        
        Story story = Story.builder()
                .id("story123")
                .userId(userId)
                .mediaUrl(Post.MediaUrl.builder()
                        .url("https://s3.example.com/story.mp4")
                        .type(Post.MediaType.VIDEO)
                        .thumbnailUrl("https://s3.example.com/story-thumb.jpg")
                        .build())
                .viewsCount(0)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .createdAt(LocalDateTime.now())
                .build();
        
        when(mediaService.uploadVideoWithResolutions(any(), eq(userId))).thenReturn(uploadResult);
        when(storyService.createStory(eq(userId), any())).thenReturn(story);
        
        // Act
        ResponseEntity<?> response = storyController.createStory(file, userId);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(mediaService, times(1)).uploadVideoWithResolutions(any(), eq(userId));
        verify(storyService, times(1)).createStory(eq(userId), any());
    }
    
    @Test
    void createStory_withUnsupportedFileType_returnsBadRequest() throws IOException {
        // Arrange
        String userId = "user123";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "test content".getBytes()
        );
        
        // Act
        ResponseEntity<?> response = storyController.createStory(file, userId);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mediaService, never()).uploadImageWithThumbnail(any(), any());
        verify(mediaService, never()).uploadVideoWithResolutions(any(), any());
        verify(storyService, never()).createStory(any(), any());
    }
    
    @Test
    void getActiveStories_returnsListOfStories() {
        // Arrange
        String userId = "user123";
        List<Story> stories = List.of(
                Story.builder()
                        .id("story1")
                        .userId(userId)
                        .viewsCount(5)
                        .expiresAt(LocalDateTime.now().plusHours(12))
                        .createdAt(LocalDateTime.now().minusHours(12))
                        .build(),
                Story.builder()
                        .id("story2")
                        .userId(userId)
                        .viewsCount(10)
                        .expiresAt(LocalDateTime.now().plusHours(6))
                        .createdAt(LocalDateTime.now().minusHours(18))
                        .build()
        );
        
        when(storyService.getActiveStoriesByUser(userId)).thenReturn(stories);
        
        // Act
        ResponseEntity<?> response = storyController.getActiveStories(userId);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(storyService, times(1)).getActiveStoriesByUser(userId);
    }
    
    @Test
    void recordStoryView_withValidData_returnsSuccess() {
        // Arrange
        String storyId = "story123";
        String viewerId = "viewer456";
        
        doNothing().when(storyService).recordStoryView(storyId, viewerId);
        
        // Act
        ResponseEntity<Map<String, String>> response = storyController.recordStoryView(storyId, viewerId);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "Story view recorded successfully");
        assertThat(response.getBody()).containsEntry("storyId", storyId);
        assertThat(response.getBody()).containsEntry("viewerId", viewerId);
        verify(storyService, times(1)).recordStoryView(storyId, viewerId);
    }
    
    @Test
    void recordStoryView_withNonExistentStory_throwsException() {
        // Arrange
        String storyId = "nonexistent";
        String viewerId = "viewer456";
        
        doThrow(new StoryNotFoundException(storyId))
                .when(storyService).recordStoryView(storyId, viewerId);
        
        // Act & Assert - Exception should be thrown and handled by GlobalExceptionHandler
        try {
            storyController.recordStoryView(storyId, viewerId);
        } catch (StoryNotFoundException e) {
            assertThat(e.getMessage()).contains(storyId);
        }
        
        verify(storyService, times(1)).recordStoryView(storyId, viewerId);
    }
    
    @Test
    void getStoryViewers_returnsListOfViewers() {
        // Arrange
        String storyId = "story123";
        List<StoryView> views = List.of(
                StoryView.builder()
                        .id("view1")
                        .storyId(storyId)
                        .viewerId("viewer1")
                        .viewedAt(LocalDateTime.now().minusHours(2))
                        .build(),
                StoryView.builder()
                        .id("view2")
                        .storyId(storyId)
                        .viewerId("viewer2")
                        .viewedAt(LocalDateTime.now().minusHours(5))
                        .build()
        );
        
        when(storyService.getStoryViewers(storyId)).thenReturn(views);
        
        // Act
        ResponseEntity<?> response = storyController.getStoryViewers(storyId);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(storyService, times(1)).getStoryViewers(storyId);
    }
    
    @Test
    void deleteStory_returnsSuccess() {
        // Arrange
        String storyId = "story123";
        String userId = "user456";
        
        doNothing().when(storyService).deleteStory(storyId);
        
        // Act
        ResponseEntity<Map<String, String>> response = storyController.deleteStory(storyId, userId);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "Story deleted successfully");
        assertThat(response.getBody()).containsEntry("storyId", storyId);
        verify(storyService, times(1)).deleteStory(storyId);
    }
}
