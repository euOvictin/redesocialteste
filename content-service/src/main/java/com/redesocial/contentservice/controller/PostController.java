package com.redesocial.contentservice.controller;

import com.redesocial.contentservice.dto.CommentResponse;
import com.redesocial.contentservice.dto.CreateCommentRequest;
import com.redesocial.contentservice.dto.CreatePostRequest;
import com.redesocial.contentservice.dto.PostResponse;
import com.redesocial.contentservice.service.CommentService;
import com.redesocial.contentservice.service.LikeService;
import com.redesocial.contentservice.service.MediaService;
import com.redesocial.contentservice.service.PostDeletionService;
import com.redesocial.contentservice.service.PostService;
import com.redesocial.contentservice.service.ShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {
    
    private final PostService postService;
    private final MediaService mediaService;
    private final PostDeletionService postDeletionService;
    private final LikeService likeService;
    private final CommentService commentService;
    private final ShareService shareService;
    
    @PostMapping
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        log.info("Received request to create post for user: {}", request.getUserId());
        PostResponse response = postService.createPost(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(@PathVariable String id) {
        log.info("Received request to get post: {}", id);
        PostResponse response = postService.getPost(id);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/media/image")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) {
        log.info("Received request to upload image for user: {}", userId);
        try {
            Map<String, String> result = mediaService.uploadImageWithThumbnail(file, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            log.error("Validation error uploading image: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Error uploading image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload image: " + e.getMessage()));
        }
    }
    
    @PostMapping("/media/video")
    public ResponseEntity<Map<String, String>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) {
        log.info("Received request to upload video for user: {}", userId);
        try {
            Map<String, String> result = mediaService.uploadVideoWithResolutions(file, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            log.error("Validation error uploading video: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Error uploading video", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload video: " + e.getMessage()));
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deletePost(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Received request to delete post: {} by user: {}", id, userId);
        postDeletionService.deletePost(id, userId);
        return ResponseEntity.ok(Map.of("message", "Post deleted successfully", "postId", id));
    }
    
    @PostMapping("/{id}/like")
    public ResponseEntity<Map<String, String>> likePost(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Received request to like post: {} by user: {}", id, userId);
        likeService.likePost(id, userId);
        return ResponseEntity.ok(Map.of("message", "Post liked successfully", "postId", id));
    }
    
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Map<String, String>> unlikePost(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Received request to unlike post: {} by user: {}", id, userId);
        likeService.unlikePost(id, userId);
        return ResponseEntity.ok(Map.of("message", "Post unliked successfully", "postId", id));
    }
    
    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable String id,
            @Valid @RequestBody CreateCommentRequest request) {
        log.info("Received request to add comment to post: {} by user: {}", id, request.getUserId());
        CommentResponse response = commentService.addComment(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}/comments")
    public ResponseEntity<Page<CommentResponse>> getComments(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Received request to get comments for post: {} (page: {}, size: {})", id, page, size);
        Page<CommentResponse> comments = commentService.getComments(id, page, size);
        return ResponseEntity.ok(comments);
    }
    
    @PostMapping("/{id}/share")
    public ResponseEntity<PostResponse> sharePost(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Received request to share post: {} by user: {}", id, userId);
        PostResponse response = shareService.sharePost(id, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
