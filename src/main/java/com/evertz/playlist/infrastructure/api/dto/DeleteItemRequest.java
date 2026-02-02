package com.evertz.playlist.infrastructure.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for deleting a playlist item.
 */
public record DeleteItemRequest(
        @NotNull(message = "clientFingerprint is required")
        String clientFingerprint
) {}
