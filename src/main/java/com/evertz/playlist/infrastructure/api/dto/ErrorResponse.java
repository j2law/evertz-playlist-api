package com.evertz.playlist.infrastructure.api.dto;

/**
 * Response DTO for error responses.
 */
public record ErrorResponse(
        String errorCode,
        String message
) {}
