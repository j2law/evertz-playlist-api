package com.evertz.playlist.core.exception;

/**
 * Domain exception thrown when client fingerprint does not match server fingerprint.
 */
public class FingerprintMismatchException extends RuntimeException {

    private final String serverFingerprint;

    public FingerprintMismatchException(String serverFingerprint) {
        super("Client fingerprint does not match server fingerprint");
        this.serverFingerprint = serverFingerprint;
    }

    public String getServerFingerprint() {
        return serverFingerprint;
    }
}
