package com.redesocial.contentservice.repository.jpa;

import com.redesocial.contentservice.model.jpa.PostMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostMetadataRepository extends JpaRepository<PostMetadata, String> {
    
    List<PostMetadata> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(String userId);
    
    Optional<PostMetadata> findByIdAndIsDeletedFalse(String id);
}
