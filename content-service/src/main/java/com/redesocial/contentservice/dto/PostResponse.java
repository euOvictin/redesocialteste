package com.redesocial.contentservice.dto;

import com.redesocial.contentservice.model.mongo.Post;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {
    
    private String id;
    private String userId;
    private String content;
    private List<Post.MediaUrl> mediaUrls;
    private List<String> hashtags;
    private Integer likesCount;
    private Integer commentsCount;
    private Integer sharesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
