package com.evertz.playlist.infrastructure.api.dto;

/**
 * Response DTO for fingerprint mismatch errors (409 Conflict).
 */
public record FingerprintMismatchResponse(
        String errorCode,
        String serverFingerprint
) {
    public FingerprintMismatchResponse(String serverFingerprint) {
        this("PLAYLIST_FINGERPRINT_MISMATCH", serverFingerprint);
    }
}
