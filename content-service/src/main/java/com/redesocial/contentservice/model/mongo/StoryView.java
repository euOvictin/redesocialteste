package com.redesocial.contentservice.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "story_views")
@CompoundIndex(name = "story_viewer_idx", def = "{'storyId': 1, 'viewerId': 1}", unique = true)
public class StoryView {
    
    @Id
    private String id;
    
    @Indexed
    private String storyId;
    
    private String viewerId;
    
    @Indexed(expireAfterSeconds = 86400) // 24 hours in seconds
    private LocalDateTime viewedAt;
}
