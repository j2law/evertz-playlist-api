package com.evertz.playlist.core.exception;

/**
 * Domain exception thrown when pagination parameters are invalid.
 */
public class InvalidPaginationException extends RuntimeException {

    public InvalidPaginationException(String message) {
        super(message);
    }
}
