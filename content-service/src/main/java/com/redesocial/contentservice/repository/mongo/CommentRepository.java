package com.redesocial.contentservice.repository.mongo;

import com.redesocial.contentservice.model.mongo.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends MongoRepository<Comment, String> {
    
    Page<Comment> findByPostIdOrderByCreatedAtDesc(String postId, Pageable pageable);
    
    long countByPostId(String postId);
}
