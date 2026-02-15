package com.redesocial.contentservice.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stories")
public class Story {
    
    @Id
    private String id;
    
    @Indexed
    private String userId;
    
    private Post.MediaUrl mediaUrl;
    
    @Builder.Default
    private Integer viewsCount = 0;
    
    @Indexed(expireAfterSeconds = 86400) // 24 hours in seconds
    private LocalDateTime expiresAt;
    
    private LocalDateTime createdAt;
}
