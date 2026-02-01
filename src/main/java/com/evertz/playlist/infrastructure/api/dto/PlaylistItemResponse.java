package com.evertz.playlist.infrastructure.api.dto;

/**
 * Response DTO for a single playlist item.
 */
public record PlaylistItemResponse(
        String itemId,
        int index,
        String title
) {}
