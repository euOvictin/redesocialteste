package com.redesocial.contentservice.event;

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
public class PostCreatedEvent {
    
    private String postId;
    private String userId;
    private String content;
    private List<String> hashtags;
    private String type;
    private LocalDateTime createdAt;
}
