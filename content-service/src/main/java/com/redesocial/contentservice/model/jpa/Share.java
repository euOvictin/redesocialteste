package com.redesocial.contentservice.model.jpa;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "shares", indexes = {
    @Index(name = "idx_shares_original_post_id", columnList = "original_post_id"),
    @Index(name = "idx_shares_user_id", columnList = "user_id")
})
public class Share {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "original_post_id", nullable = false, length = 36)
    private String originalPostId;
    
    @Column(name = "shared_post_id", nullable = false, length = 36)
    private String sharedPostId;
    
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
