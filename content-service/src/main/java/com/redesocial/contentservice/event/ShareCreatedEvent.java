package com.redesocial.contentservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareCreatedEvent {
    
    private String originalPostId;
    private String sharedPostId;
    private String userId;
    private String originalAuthorId;
    private LocalDateTime createdAt;
}
