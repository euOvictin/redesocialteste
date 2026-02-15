package com.redesocial.contentservice.repository.mongo;

import com.redesocial.contentservice.model.mongo.Story;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StoryRepository extends MongoRepository<Story, String> {
    
    @Query("{ 'userId': ?0, 'expiresAt': { $gt: ?1 } }")
    List<Story> findActiveStoriesByUserId(String userId, LocalDateTime now);
    
    @Query("{ 'expiresAt': { $gt: ?0 } }")
    List<Story> findAllActiveStories(LocalDateTime now);
}
