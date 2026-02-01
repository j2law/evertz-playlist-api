package com.evertz.playlist.infrastructure.api.controller;

import com.evertz.playlist.core.exception.FingerprintMismatchException;
import com.evertz.playlist.core.exception.InvalidIndexException;
import com.evertz.playlist.core.exception.InvalidPaginationException;
import com.evertz.playlist.core.exception.ResourceNotFoundException;
import com.evertz.playlist.infrastructure.api.dto.ErrorResponse;
import com.evertz.playlist.infrastructure.api.dto.FingerprintMismatchResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for REST API.
 * Handles exceptions globally across all controllers given specific exceptions and returns appropriate HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FingerprintMismatchException.class)
    public ResponseEntity<FingerprintMismatchResponse> handleFingerprintMismatch(FingerprintMismatchException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new FingerprintMismatchResponse(ex.getServerFingerprint()));
    }

    @ExceptionHandler(InvalidIndexException.class)
    public ResponseEntity<ErrorResponse> handleInvalidIndex(InvalidIndexException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_INDEX", ex.getMessage()));
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPagination(InvalidPaginationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_PAGINATION", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }
}
