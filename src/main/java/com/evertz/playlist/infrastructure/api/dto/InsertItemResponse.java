package com.evertz.playlist.infrastructure.api.dto;

/**
 * Response DTO for insert item operation.
 */
public record InsertItemResponse(
        PlaylistItemResponse item,
        String serverFingerprint
) {}
