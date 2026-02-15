package com.redesocial.contentservice.service;

import com.redesocial.contentservice.model.mongo.Post;
import com.redesocial.contentservice.model.mongo.Story;
import com.redesocial.contentservice.model.mongo.StoryView;
import com.redesocial.contentservice.repository.mongo.StoryRepository;
import com.redesocial.contentservice.repository.mongo.StoryViewRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for StoryService
 * Feature: rede-social-brasileira
 */
class StoryServiceProperties {
    
    private StoryRepository storyRepository;
    private StoryViewRepository storyViewRepository;
    private StoryService storyService;
    
    @BeforeEach
    void setUp() {
        storyRepository = Mockito.mock(StoryRepository.class);
        storyViewRepository = Mockito.mock(StoryViewRepository.class);
        storyService = new StoryService(storyRepository, storyViewRepository);
    }
    
    /**
     * Property 13: Story criado tem expiração de 24 horas
     * **Validates: Requirements 3.1**
     * 
     * Para qualquer story publicado, o timestamp de expiração deve ser exatamente 
     * 24 horas após criação
     */
    @Property(tries = 100)
    void storyHas24HourExpiration(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId,
            @ForAll @AlphaChars @StringLength(min = 10, max = 100) String mediaUrl
    ) {
        // Arrange
        Post.MediaUrl media = Post.MediaUrl.builder()
                .url(mediaUrl)
                .type(Post.MediaType.IMAGE)
                .build();
        
        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        LocalDateTime beforeCreation = LocalDateTime.now();
        Story story = storyService.createStory(userId, media);
        LocalDateTime afterCreation = LocalDateTime.now();
        
        // Assert
        assertThat(story).isNotNull();
        assertThat(story.getId()).isNotNull();
        assertThat(story.getUserId()).isEqualTo(userId);
        assertThat(story.getExpiresAt()).isNotNull();
        assertThat(story.getCreatedAt()).isNotNull();
        
        // Verify expiration is 24 hours after creation
        long hoursDifference = ChronoUnit.HOURS.between(story.getCreatedAt(), story.getExpiresAt());
        assertThat(hoursDifference).isEqualTo(24);
        
        // Verify creation time is within test execution window
        assertThat(story.getCreatedAt()).isBetween(beforeCreation, afterCreation);
        
        // Verify expiration is 24 hours from creation
        LocalDateTime expectedExpiration = story.getCreatedAt().plusHours(24);
        assertThat(story.getExpiresAt()).isEqualTo(expectedExpiration);
    }
    
    /**
     * Property 14: Visualização retorna apenas stories não expirados
     * **Validates: Requirements 3.3**
     * 
     * Para qualquer conjunto de stories (expirados e não expirados), a consulta deve 
     * retornar apenas stories não expirados ordenados por timestamp
     */
    @Property(tries = 100)
    void onlyActiveStoriesAreReturned(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId
    ) {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        
        // Create mock active stories (not expired)
        Story activeStory1 = Story.builder()
                .id("active1")
                .userId(userId)
                .expiresAt(now.plusHours(12))
                .createdAt(now.minusHours(12))
                .build();
        
        Story activeStory2 = Story.builder()
                .id("active2")
                .userId(userId)
                .expiresAt(now.plusHours(6))
                .createdAt(now.minusHours(18))
                .build();
        
        List<Story> activeStories = List.of(activeStory1, activeStory2);
        
        when(storyRepository.findActiveStoriesByUserId(eq(userId), any(LocalDateTime.class)))
                .thenReturn(activeStories);
        
        // Act
        List<Story> result = storyService.getActiveStoriesByUser(userId);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(activeStory1, activeStory2);
        
        // Verify all returned stories are not expired
        for (Story story : result) {
            assertThat(story.getExpiresAt()).isAfter(now);
        }
    }
    
    /**
     * Property 15: Visualizações de stories são registradas
     * **Validates: Requirements 3.4**
     * 
     * Para qualquer visualização de story, o sistema deve registrar ID do visualizador 
     * e timestamp
     */
    @Property(tries = 100)
    void storyViewsAreRecorded(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String storyId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String viewerId
    ) {
        // Arrange
        Story story = Story.builder()
                .id(storyId)
                .userId("author")
                .viewsCount(0)
                .build();
        
        when(storyViewRepository.findByStoryIdAndViewerId(storyId, viewerId))
                .thenReturn(Optional.empty());
        when(storyViewRepository.save(any(StoryView.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        LocalDateTime beforeView = LocalDateTime.now();
        storyService.recordStoryView(storyId, viewerId);
        LocalDateTime afterView = LocalDateTime.now();
        
        // Assert - Verify view was saved
        verify(storyViewRepository, times(1)).save(any(StoryView.class));
        
        // Verify story views count was incremented
        verify(storyRepository, times(1)).save(any(Story.class));
    }
    
    /**
     * Property 16: Lista de visualizadores filtra por 24 horas
     * **Validates: Requirements 3.5**
     * 
     * Para qualquer story, a lista de visualizadores deve incluir apenas usuários que 
     * visualizaram nos últimos 24 horas
     */
    @Property(tries = 100)
    void viewersListFilteredBy24Hours(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String storyId
    ) {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twentyFourHoursAgo = now.minusHours(24);
        
        // Create mock recent views (within 24 hours)
        StoryView recentView1 = StoryView.builder()
                .id("view1")
                .storyId(storyId)
                .viewerId("viewer1")
                .viewedAt(now.minusHours(12))
                .build();
        
        StoryView recentView2 = StoryView.builder()
                .id("view2")
                .storyId(storyId)
                .viewerId("viewer2")
                .viewedAt(now.minusHours(6))
                .build();
        
        List<StoryView> recentViews = List.of(recentView1, recentView2);
        
        when(storyViewRepository.findRecentViewsByStoryId(eq(storyId), any(LocalDateTime.class)))
                .thenReturn(recentViews);
        
        // Act
        List<StoryView> result = storyService.getStoryViewers(storyId);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(recentView1, recentView2);
        
        // Verify all views are within 24 hours
        for (StoryView view : result) {
            assertThat(view.getViewedAt()).isAfter(twentyFourHoursAgo);
        }
    }
    
    /**
     * Additional property: Story view is idempotent
     * Verifies that viewing the same story multiple times by the same user 
     * only records one view
     */
    @Property(tries = 100)
    void storyViewIsIdempotent(
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String storyId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String viewerId
    ) {
        // Arrange - First view
        Story story = Story.builder()
                .id(storyId)
                .userId("author")
                .viewsCount(0)
                .build();
        
        when(storyViewRepository.findByStoryIdAndViewerId(storyId, viewerId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(StoryView.builder().build())); // Second call returns existing view
        
        when(storyViewRepository.save(any(StoryView.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act - View twice
        storyService.recordStoryView(storyId, viewerId);
        storyService.recordStoryView(storyId, viewerId);
        
        // Assert - View should only be saved once
        verify(storyViewRepository, times(1)).save(any(StoryView.class));
    }
}
