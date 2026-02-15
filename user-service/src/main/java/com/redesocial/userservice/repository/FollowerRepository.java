package com.redesocial.userservice.repository;

import com.redesocial.userservice.model.Follower;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FollowerRepository extends JpaRepository<Follower, String> {
    
    Optional<Follower> findByFollowerIdAndFollowingId(String followerId, String followingId);
    
    boolean existsByFollowerIdAndFollowingId(String followerId, String followingId);
    
    Page<Follower> findByFollowingId(String followingId, Pageable pageable);
    
    Page<Follower> findByFollowerId(String followerId, Pageable pageable);
    
    long countByFollowingId(String followingId);
    
    long countByFollowerId(String followerId);
}
