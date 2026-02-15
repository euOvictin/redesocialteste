package com.redesocial.contentservice.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HashtagExtractor
 */
class HashtagExtractorTest {
    
    @Test
    void extractHashtags_withNoHashtags_shouldReturnEmptyList() {
        String content = "This is a post without hashtags";
        List<String> hashtags = HashtagExtractor.extractHashtags(content);
        assertThat(hashtags).isEmpty();
    }
    
    @Test
    void extractHashtags_withSingleHashtag_shouldExtractIt() {
        String content = "This is a post with #java";
        List<String> hashtags = HashtagExtractor.extractHashtags(content);
        assertThat(hashtags).containsExactly("java");
    }
    
    @Test
    void extractHashtags_withMultipleHashtags_shouldExtractAll() {
        String content = "Post with #java #spring #boot";
        List<String> hashtags = HashtagExtractor.extractHashtags(content);
        assertThat(hashtags).containsExactly("java", "spring", "boot");
    }
    
    @Test
    void extractHashtags_withDuplicates_shouldDeduplicateHashtags() {
        String content = "Post with #java #spring #java";
        List<String> hashtags = HashtagExtractor.extractHashtags(content);
        assertThat(hashtags).containsExactly("java", "spring");
    }
    
    @Test
    void extractHashtags_shouldConvertToLowercase() {
        String content = "Post with #Java #SPRING #Boot";
        List<String> hashtags = HashtagExtractor.extractHashtags(content);
        assertThat(hashtags).containsExactly("java", "spring", "boot");
    }
    
    @Test
    void extractHashtags_withNullContent_shouldReturnEmptyList() {
        List<String> hashtags = HashtagExtractor.extractHashtags(null);
        assertThat(hashtags).isEmpty();
    }
    
    @Test
    void extractHashtags_withEmptyContent_shouldReturnEmptyList() {
        List<String> hashtags = HashtagExtractor.extractHashtags("");
        assertThat(hashtags).isEmpty();
    }
    
    @Test
    void extractHashtags_withHashtagAtStart_shouldExtract() {
        String content = "#java is awesome";
        List<String> hashtags = HashtagExtractor.extractHashtags(content);
        assertThat(hashtags).containsExactly("java");
    }
    
    @Test
    void extractHashtags_withHashtagAtEnd_shouldExtract() {
        String content = "Learning #java";
        List<String> hashtags = HashtagExtractor.extractHashtags(content);
        assertThat(hashtags).containsExactly("java");
    }
    
    @Test
    void extractHashtags_withNumbersInHashtag_shouldExtract() {
        String content = "Post with #java17 and #spring3";
        List<String> hashtags = HashtagExtractor.extractHashtags(content);
        assertThat(hashtags).containsExactly("java17", "spring3");
    }
    
    @Test
    void extractHashtags_withUnderscoreInHashtag_shouldExtract() {
        String content = "Post with #spring_boot and #java_dev";
        List<String> hashtags = HashtagExtractor.extractHashtags(content);
        assertThat(hashtags).containsExactly("spring_boot", "java_dev");
    }
}
