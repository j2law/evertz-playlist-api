package com.evertz.playlist.infrastructure.api.dto;

/**
 * Request DTO for deleting a playlist item.
 */
public record DeleteItemRequest(
        String clientFingerprint
) {}
