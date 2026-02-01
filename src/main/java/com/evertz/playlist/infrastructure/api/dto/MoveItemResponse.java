package com.evertz.playlist.infrastructure.api.dto;

/**
 * Response DTO for move item operation.
 */
public record MoveItemResponse(
        PlaylistItemResponse item,
        String serverFingerprint
) {}
