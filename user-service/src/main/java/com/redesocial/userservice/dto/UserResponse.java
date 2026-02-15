package com.redesocial.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String id;
    
    private String email;
    
    private String name;
    
    private String bio;
    
    private String profilePictureUrl;
    
    private Integer followersCount;
    
    private Integer followingCount;
    
    private Boolean isPrivate;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
