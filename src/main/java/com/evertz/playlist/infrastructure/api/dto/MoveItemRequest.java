package com.evertz.playlist.infrastructure.api.dto;

/**
 * Request DTO for moving a playlist item to a new index.
 */
public record MoveItemRequest(
        int newIndex,
        String clientFingerprint
) {}
