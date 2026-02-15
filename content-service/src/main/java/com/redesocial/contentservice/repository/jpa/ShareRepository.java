package com.redesocial.contentservice.repository.jpa;

import com.redesocial.contentservice.model.jpa.Share;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShareRepository extends JpaRepository<Share, Long> {
    
    List<Share> findByOriginalPostId(String originalPostId);
    
    List<Share> findByUserId(String userId);
    
    long countByOriginalPostId(String originalPostId);
}
