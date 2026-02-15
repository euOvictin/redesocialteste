package com.redesocial.contentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redesocial.contentservice.dto.CommentResponse;
import com.redesocial.contentservice.dto.CreateCommentRequest;
import com.redesocial.contentservice.service.CommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostController.class)
class CommentControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private CommentService commentService;
    
    @MockBean
    private com.redesocial.contentservice.service.PostService postService;
    
    @MockBean
    private com.redesocial.contentservice.service.MediaService mediaService;
    
    @MockBean
    private com.redesocial.contentservice.service.PostDeletionService postDeletionService;
    
    @MockBean
    private com.redesocial.contentservice.service.LikeService likeService;
    
    @Test
    void addComment_WithValidRequest_ReturnsCreated() throws Exception {
        // Given
        CreateCommentRequest request = CreateCommentRequest.builder()
                .userId("user-123")
                .content("Great post!")
                .build();
        
        CommentResponse response = CommentResponse.builder()
                .id("comment-456")
                .postId("post-789")
                .userId("user-123")
                .content("Great post!")
                .likesCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(commentService.addComment(eq("post-789"), any(CreateCommentRequest.class)))
                .thenReturn(response);
        
        // When/Then
        mockMvc.perform(post("/api/posts/post-789/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("comment-456"))
                .andExpect(jsonPath("$.postId").value("post-789"))
                .andExpect(jsonPath("$.userId").value("user-123"))
                .andExpect(jsonPath("$.content").value("Great post!"))
                .andExpect(jsonPath("$.likesCount").value(0));
    }
    
    @Test
    void addComment_WithEmptyContent_ReturnsBadRequest() throws Exception {
        // Given
        CreateCommentRequest request = CreateCommentRequest.builder()
                .userId("user-123")
                .content("")
                .build();
        
        // When/Then
        mockMvc.perform(post("/api/posts/post-789/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void addComment_WithTooLongContent_ReturnsBadRequest() throws Exception {
        // Given
        String longContent = "A".repeat(1001);
        CreateCommentRequest request = CreateCommentRequest.builder()
                .userId("user-123")
                .content(longContent)
                .build();
        
        // When/Then
        mockMvc.perform(post("/api/posts/post-789/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void getComments_WithValidPostId_ReturnsPagedComments() throws Exception {
        // Given
        CommentResponse comment1 = CommentResponse.builder()
                .id("comment-1")
                .postId("post-789")
                .userId("user-1")
                .content("First comment")
                .likesCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        CommentResponse comment2 = CommentResponse.builder()
                .id("comment-2")
                .postId("post-789")
                .userId("user-2")
                .content("Second comment")
                .likesCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        Page<CommentResponse> page = new PageImpl<>(Arrays.asList(comment1, comment2));
        
        when(commentService.getComments(eq("post-789"), anyInt(), anyInt()))
                .thenReturn(page);
        
        // When/Then
        mockMvc.perform(get("/api/posts/post-789/comments")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value("comment-1"))
                .andExpect(jsonPath("$.content[1].id").value("comment-2"));
    }
    
    @Test
    void getComments_WithDefaultPagination_ReturnsComments() throws Exception {
        // Given
        Page<CommentResponse> page = new PageImpl<>(Arrays.asList());
        
        when(commentService.getComments(eq("post-789"), eq(0), eq(20)))
                .thenReturn(page);
        
        // When/Then
        mockMvc.perform(get("/api/posts/post-789/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
