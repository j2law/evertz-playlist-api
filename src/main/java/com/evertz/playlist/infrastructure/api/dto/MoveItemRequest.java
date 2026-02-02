package com.evertz.playlist.infrastructure.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for moving a playlist item to a new index.
 */
public record MoveItemRequest(
        int newIndex,
        @NotNull(message = "clientFingerprint is required")
        String clientFingerprint
) {}
