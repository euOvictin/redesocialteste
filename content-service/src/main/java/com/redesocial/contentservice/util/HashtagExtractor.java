package com.redesocial.contentservice.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HashtagExtractor {
    
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#(\\w+)");
    
    public static List<String> extractHashtags(String content) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> hashtags = new ArrayList<>();
        Matcher matcher = HASHTAG_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String hashtag = matcher.group(1).toLowerCase();
            if (!hashtags.contains(hashtag)) {
                hashtags.add(hashtag);
            }
        }
        
        return hashtags;
    }
}
