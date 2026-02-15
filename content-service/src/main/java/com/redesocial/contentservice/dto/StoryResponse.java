package com.redesocial.contentservice.dto;

import com.redesocial.contentservice.model.mongo.Post;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryResponse {
    
    private String id;
    private String userId;
    private Post.MediaUrl mediaUrl;
    private Integer viewsCount;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
