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
public class PostDeletedEvent {
    
    private String postId;
    private String userId;
    private LocalDateTime deletedAt;
}
