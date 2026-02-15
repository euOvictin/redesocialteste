package com.redesocial.contentservice.repository.jpa;

import com.redesocial.contentservice.model.jpa.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {
    
    Optional<Like> findByPostIdAndUserId(String postId, String userId);
    
    boolean existsByPostIdAndUserId(String postId, String userId);
    
    long countByPostId(String postId);
    
    void deleteByPostIdAndUserId(String postId, String userId);
}
