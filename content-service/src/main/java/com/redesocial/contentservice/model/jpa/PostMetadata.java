package com.redesocial.contentservice.model.jpa;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "post_metadata", indexes = {
    @Index(name = "idx_post_metadata_user_id", columnList = "user_id"),
    @Index(name = "idx_post_metadata_created_at", columnList = "created_at"),
    @Index(name = "idx_post_metadata_is_deleted", columnList = "is_deleted")
})
public class PostMetadata {
    
    @Id
    private String id;
    
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostType type;
    
    @Builder.Default
    @Column(name = "likes_count", nullable = false)
    private Integer likesCount = 0;
    
    @Builder.Default
    @Column(name = "comments_count", nullable = false)
    private Integer commentsCount = 0;
    
    @Builder.Default
    @Column(name = "shares_count", nullable = false)
    private Integer sharesCount = 0;
    
    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public enum PostType {
        TEXT,
        IMAGE,
        VIDEO,
        MIXED
    }
}
