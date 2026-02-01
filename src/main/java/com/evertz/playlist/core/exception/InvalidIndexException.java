package com.evertz.playlist.core.exception;

/**
 * Domain exception thrown when an invalid index is provided for playlist operations.
 */
public class InvalidIndexException extends RuntimeException {

    public InvalidIndexException(String message) {
        super(message);
    }
}
