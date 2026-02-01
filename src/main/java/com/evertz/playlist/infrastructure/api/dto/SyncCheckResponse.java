package com.evertz.playlist.infrastructure.api.dto;

/**
 * Response DTO for sync-check operation when fingerprints match.
 */
public record SyncCheckResponse(
        String serverFingerprint
) {}
