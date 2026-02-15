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
@Document(collection = "comments")
public class Comment {
    
    @Id
    private String id;
    
    @Indexed
    private String postId;
    
    private String userId;
    
    private String content;
    
    @Builder.Default
    private Integer likesCount = 0;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
