package com.redesocial.contentservice.repository.mongo;

import com.redesocial.contentservice.model.mongo.StoryView;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoryViewRepository extends MongoRepository<StoryView, String> {
    
    Optional<StoryView> findByStoryIdAndViewerId(String storyId, String viewerId);
    
    @Query("{ 'storyId': ?0, 'viewedAt': { $gt: ?1 } }")
    List<StoryView> findRecentViewsByStoryId(String storyId, LocalDateTime since);
    
    long countByStoryId(String storyId);
}
