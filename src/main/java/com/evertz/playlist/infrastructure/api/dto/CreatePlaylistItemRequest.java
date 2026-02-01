package com.evertz.playlist.infrastructure.api.dto;

/**
 * Request DTO for creating a new playlist item.
 */
public record CreatePlaylistItemRequest(
        String title,
        int index,
        String clientFingerprint
) {}
