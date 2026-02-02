package com.evertz.playlist.infrastructure.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for sync-check operation.
 */
public record SyncCheckRequest(
        @NotNull(message = "clientFingerprint is required")
        String clientFingerprint
) {}
