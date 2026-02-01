package com.evertz.playlist.infrastructure.api.dto;

/**
 * Response DTO for pagination information.
 */
public record PageInfo(
        int limit,
        int offset,
        Integer nextOffset,
        boolean hasMore
) {}
