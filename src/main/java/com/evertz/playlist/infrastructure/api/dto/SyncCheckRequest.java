package com.evertz.playlist.infrastructure.api.dto;

/**
 * Request DTO for sync-check operation.
 */
public record SyncCheckRequest(
        String clientFingerprint
) {}
