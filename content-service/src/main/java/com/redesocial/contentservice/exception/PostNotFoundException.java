package com.redesocial.contentservice.exception;

public class PostNotFoundException extends RuntimeException {
    
    public PostNotFoundException(String postId) {
        super("Post not found: " + postId);
    }
}
