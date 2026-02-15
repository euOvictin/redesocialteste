package com.redesocial.contentservice.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "posts")
public class Post {
    
    @Id
    private String id;
    
    private String userId;
    
    private String content;
    
    @Builder.Default
    private List<MediaUrl> mediaUrls = new ArrayList<>();
    
    @Builder.Default
    private List<String> hashtags = new ArrayList<>();
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaUrl {
        private String url;
        private MediaType type;
        private String thumbnailUrl;
        private Integer width;
        private Integer height;
        private Integer duration; // for videos in seconds
    }
    
    public enum MediaType {
        IMAGE,
        VIDEO
    }
}
