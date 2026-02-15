package com.redesocial.contentservice.controller;

import com.redesocial.contentservice.dto.StoryResponse;
import com.redesocial.contentservice.dto.StoryViewerResponse;
import com.redesocial.contentservice.model.mongo.Post;
import com.redesocial.contentservice.model.mongo.Story;
import com.redesocial.contentservice.model.mongo.StoryView;
import com.redesocial.contentservice.service.MediaService;
import com.redesocial.contentservice.service.StoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryController {
    
    private final StoryService storyService;
    private final MediaService mediaService;
    
    @PostMapping
    public ResponseEntity<?> createStory(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) {
        log.info("Received request to create story for user: {}", userId);
        
        try {
            // Determine if file is image or video based on content type
            String contentType = file.getContentType();
            if (contentType == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "File content type is required"));
            }
            
            Map<String, String> uploadResult;
            Post.MediaUrl mediaUrl;
            
            if (contentType.startsWith("image/")) {
                // Upload image with thumbnail
                uploadResult = mediaService.uploadImageWithThumbnail(file, userId);
                mediaUrl = Post.MediaUrl.builder()
                        .url(uploadResult.get("url"))
                        .type(Post.MediaType.IMAGE)
                        .thumbnailUrl(uploadResult.get("thumbnailUrl"))
                        .build();
            } else if (contentType.startsWith("video/")) {
                // Upload video with resolutions
                uploadResult = mediaService.uploadVideoWithResolutions(file, userId);
                mediaUrl = Post.MediaUrl.builder()
                        .url(uploadResult.get("url"))
                        .type(Post.MediaType.VIDEO)
                        .thumbnailUrl(uploadResult.get("thumbnailUrl"))
                        .build();
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Unsupported file type. Only images and videos are allowed"));
            }
            
            // Create story
            Story story = storyService.createStory(userId, mediaUrl);
            StoryResponse response = mapToStoryResponse(story);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating story: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Error uploading story media", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload story media: " + e.getMessage()));
        }
    }
    
    @GetMapping("/{userId}")
    public ResponseEntity<List<StoryResponse>> getActiveStories(@PathVariable String userId) {
        log.info("Received request to get active stories for user: {}", userId);
        
        List<Story> stories = storyService.getActiveStoriesByUser(userId);
        List<StoryResponse> responses = stories.stream()
                .map(this::mapToStoryResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }
    
    @PostMapping("/{storyId}/view")
    public ResponseEntity<Map<String, String>> recordStoryView(
            @PathVariable String storyId,
            @RequestHeader("X-User-Id") String viewerId) {
        log.info("Received request to record view for story: {} by viewer: {}", storyId, viewerId);
        
        storyService.recordStoryView(storyId, viewerId);
        return ResponseEntity.ok(Map.of(
                "message", "Story view recorded successfully",
                "storyId", storyId,
                "viewerId", viewerId
        ));
    }
    
    @GetMapping("/{storyId}/viewers")
    public ResponseEntity<List<StoryViewerResponse>> getStoryViewers(@PathVariable String storyId) {
        log.info("Received request to get viewers for story: {}", storyId);
        
        List<StoryView> views = storyService.getStoryViewers(storyId);
        List<StoryViewerResponse> responses = views.stream()
                .map(view -> StoryViewerResponse.builder()
                        .viewerId(view.getViewerId())
                        .viewedAt(view.getViewedAt())
                        .build())
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }
    
    @DeleteMapping("/{storyId}")
    public ResponseEntity<Map<String, String>> deleteStory(
            @PathVariable String storyId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Received request to delete story: {} by user: {}", storyId, userId);
        
        // Note: In production, you should verify that the userId owns the story
        storyService.deleteStory(storyId);
        
        return ResponseEntity.ok(Map.of(
                "message", "Story deleted successfully",
                "storyId", storyId
        ));
    }
    
    private StoryResponse mapToStoryResponse(Story story) {
        return StoryResponse.builder()
                .id(story.getId())
                .userId(story.getUserId())
                .mediaUrl(story.getMediaUrl())
                .viewsCount(story.getViewsCount())
                .expiresAt(story.getExpiresAt())
                .createdAt(story.getCreatedAt())
                .build();
    }
}
