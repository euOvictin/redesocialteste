package com.redesocial.contentservice.controller;

import com.redesocial.contentservice.dto.PostResponse;
import com.redesocial.contentservice.exception.PostNotFoundException;
import com.redesocial.contentservice.exception.UnauthorizedAccessException;
import com.redesocial.contentservice.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostController.class)
class PostControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private PostService postService;
    
    @MockBean
    private MediaService mediaService;
    
    @MockBean
    private PostDeletionService postDeletionService;
    
    @MockBean
    private LikeService likeService;
    
    @MockBean
    private CommentService commentService;
    
    @MockBean
    private ShareService shareService;
    
    @Test
    void uploadImage_ValidImage_ReturnsCreated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                "test image content".getBytes()
        );
        
        Map<String, String> result = new HashMap<>();
        result.put("imageUrl", "https://bucket.s3.amazonaws.com/images/user123/image.png");
        result.put("thumbnailUrl", "https://bucket.s3.amazonaws.com/thumbnails/user123/thumb.jpg");
        
        when(mediaService.uploadImageWithThumbnail(any(), eq("user123"))).thenReturn(result);
        
        mockMvc.perform(multipart("/api/posts/media/image")
                        .file(file)
                        .param("userId", "user123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value(result.get("imageUrl")))
                .andExpect(jsonPath("$.thumbnailUrl").value(result.get("thumbnailUrl")));
    }
    
    @Test
    void uploadImage_InvalidFile_ReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "not an image".getBytes()
        );
        
        when(mediaService.uploadImageWithThumbnail(any(), eq("user123")))
                .thenThrow(new IllegalArgumentException("INVALID_FILE_FORMAT: File must be JPEG, PNG, or WebP"));
        
        mockMvc.perform(multipart("/api/posts/media/image")
                        .file(file)
                        .param("userId", "user123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_FILE_FORMAT: File must be JPEG, PNG, or WebP"));
    }
    
    @Test
    void uploadImage_FileTooLarge_ReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.png",
                "image/png",
                new byte[11 * 1024 * 1024] // 11MB
        );
        
        when(mediaService.uploadImageWithThumbnail(any(), eq("user123")))
                .thenThrow(new IllegalArgumentException("FILE_TOO_LARGE: Image exceeds 10MB"));
        
        mockMvc.perform(multipart("/api/posts/media/image")
                        .file(file)
                        .param("userId", "user123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FILE_TOO_LARGE: Image exceeds 10MB"));
    }
    
    @Test
    void uploadVideo_ValidVideo_ReturnsCreated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.mp4",
                "video/mp4",
                "test video content".getBytes()
        );
        
        Map<String, String> result = new HashMap<>();
        result.put("originalUrl", "https://bucket.s3.amazonaws.com/videos/user123/video.mp4");
        result.put("480p", "https://bucket.s3.amazonaws.com/videos/user123/video_480p.mp4");
        result.put("720p", "https://bucket.s3.amazonaws.com/videos/user123/video_720p.mp4");
        result.put("1080p", "https://bucket.s3.amazonaws.com/videos/user123/video_1080p.mp4");
        
        when(mediaService.uploadVideoWithResolutions(any(), eq("user123"))).thenReturn(result);
        
        mockMvc.perform(multipart("/api/posts/media/video")
                        .file(file)
                        .param("userId", "user123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalUrl").value(result.get("originalUrl")))
                .andExpect(jsonPath("$.480p").exists())
                .andExpect(jsonPath("$.720p").exists())
                .andExpect(jsonPath("$.1080p").exists());
    }
    
    @Test
    void uploadVideo_InvalidFormat_ReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.avi",
                "video/avi",
                "not a valid format".getBytes()
        );
        
        when(mediaService.uploadVideoWithResolutions(any(), eq("user123")))
                .thenThrow(new IllegalArgumentException("INVALID_FILE_FORMAT: File must be MP4 or WebM"));
        
        mockMvc.perform(multipart("/api/posts/media/video")
                        .file(file)
                        .param("userId", "user123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_FILE_FORMAT: File must be MP4 or WebM"));
    }
    
    @Test
    void uploadVideo_FileTooLarge_ReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.mp4",
                "video/mp4",
                new byte[101 * 1024 * 1024] // 101MB
        );
        
        when(mediaService.uploadVideoWithResolutions(any(), eq("user123")))
                .thenThrow(new IllegalArgumentException("FILE_TOO_LARGE: Video exceeds 100MB"));
        
        mockMvc.perform(multipart("/api/posts/media/video")
                        .file(file)
                        .param("userId", "user123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FILE_TOO_LARGE: Video exceeds 100MB"));
    }
    
    @Test
    void deletePost_ValidRequest_ReturnsOk() throws Exception {
        String postId = "post123";
        String userId = "user123";
        
        doNothing().when(postDeletionService).deletePost(postId, userId);
        
        mockMvc.perform(delete("/api/posts/{id}", postId)
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Post deleted successfully"))
                .andExpect(jsonPath("$.postId").value(postId));
    }
    
    @Test
    void deletePost_PostNotFound_ReturnsNotFound() throws Exception {
        String postId = "nonexistent";
        String userId = "user123";
        
        doThrow(new PostNotFoundException(postId))
                .when(postDeletionService).deletePost(postId, userId);
        
        mockMvc.perform(delete("/api/posts/{id}", postId)
                        .header("X-User-Id", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Post not found: " + postId));
    }
    
    @Test
    void deletePost_UnauthorizedUser_ReturnsForbidden() throws Exception {
        String postId = "post123";
        String userId = "wrongUser";
        
        doThrow(new UnauthorizedAccessException("User not authorized to delete this post"))
                .when(postDeletionService).deletePost(postId, userId);
        
        mockMvc.perform(delete("/api/posts/{id}", postId)
                        .header("X-User-Id", userId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"))
                .andExpect(jsonPath("$.message").value("User not authorized to delete this post"));
    }
    
    @Test
    void sharePost_ValidRequest_ReturnsCreated() throws Exception {
        String postId = "post123";
        String userId = "user456";
        
        PostResponse sharedPost = PostResponse.builder()
                .id("shared789")
                .userId(userId)
                .content("Shared: Original content")
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .build();
        
        when(shareService.sharePost(postId, userId)).thenReturn(sharedPost);
        
        mockMvc.perform(post("/api/posts/{id}/share", postId)
                        .header("X-User-Id", userId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("shared789"))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.content").value("Shared: Original content"))
                .andExpect(jsonPath("$.likesCount").value(0))
                .andExpect(jsonPath("$.commentsCount").value(0))
                .andExpect(jsonPath("$.sharesCount").value(0));
    }
    
    @Test
    void sharePost_PostNotFound_ReturnsNotFound() throws Exception {
        String postId = "nonexistent";
        String userId = "user456";
        
        when(shareService.sharePost(postId, userId))
                .thenThrow(new PostNotFoundException("Post not found: " + postId));
        
        mockMvc.perform(post("/api/posts/{id}/share", postId)
                        .header("X-User-Id", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Post not found: " + postId));
    }
}
