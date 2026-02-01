package com.evertz.playlist.infrastructure.api.dto;

import java.util.List;

/**
 * Response DTO for paginated playlist items.
 */
public record PlaylistResponse(
        List<PlaylistItemResponse> items,
        PageInfo page,
        int totalCount,
        String serverFingerprint
) {}
