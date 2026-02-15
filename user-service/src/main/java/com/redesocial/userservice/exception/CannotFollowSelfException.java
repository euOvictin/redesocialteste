package com.redesocial.userservice.exception;

public class CannotFollowSelfException extends RuntimeException {
    public CannotFollowSelfException(String message) {
        super(message);
    }
}
