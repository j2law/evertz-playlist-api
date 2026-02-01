package com.evertz.playlist.application.service;

import com.evertz.playlist.application.port.PlaylistItemPort;
import com.evertz.playlist.core.exception.FingerprintMismatchException;
import com.evertz.playlist.core.exception.InvalidIndexException;
import com.evertz.playlist.core.exception.InvalidPaginationException;
import com.evertz.playlist.core.model.PlaylistItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

/**
 * Service for playlist operations.
 */
@Service
public class PlaylistService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final PlaylistItemPort playlistItemPort;

    public PlaylistService(PlaylistItemPort playlistItemPort) {
        this.playlistItemPort = playlistItemPort;
    }

    /**
     * Result record containing paginated playlist items and metadata.
     */
    public record PlaylistResult(
            List<PlaylistItem> items,
            int totalCount,
            String serverFingerprint
    ) {}

    /**
     * Result record for insert operation.
     */
    public record InsertResult(
            PlaylistItem item,
            String serverFingerprint
    ) {}

    /**
     * Gets paginated playlist items for a channel.
     *
     * @param channelId the channel identifier
     * @param offset    the number of items to skip (0-based)
     * @param limit     the maximum number of items to return
     * @return the playlist result with items, total count, and fingerprint
     */
    public PlaylistResult getItems(String channelId, int offset, int limit) {
        validatePagination(offset, limit);

        List<PlaylistItem> items = playlistItemPort.findByChannelId(channelId, offset, limit);
        int totalCount = playlistItemPort.countByChannelId(channelId);
        String fingerprint = computeFingerprint(channelId);

        return new PlaylistResult(items, totalCount, fingerprint);
    }

    /**
     * Inserts a new item at the specified index.
     * Validates fingerprint, shifts existing items, and saves the new item.
     *
     * @param channelId         the channel identifier
     * @param title             the item title
     * @param index             the target index (0-based)
     * @param clientFingerprint the client's last-known fingerprint
     * @return the insert result with the new item and updated fingerprint
     */
    @Transactional
    public InsertResult insertItem(String channelId, String title, int index, String clientFingerprint) {
        String serverFingerprint = computeFingerprint(channelId);

        if (!serverFingerprint.equals(clientFingerprint)) {
            throw new FingerprintMismatchException(serverFingerprint);
        }

        int totalCount = playlistItemPort.countByChannelId(channelId);
        validateInsertIndex(index, totalCount);

        // Shift existing items at or after the target index
        playlistItemPort.shiftIndexes(channelId, index, 1);

        // Create and save the new item
        String itemId = UUID.randomUUID().toString();
        PlaylistItem newItem = new PlaylistItem(itemId, channelId, title, index);
        PlaylistItem savedItem = playlistItemPort.save(newItem);

        String newFingerprint = computeFingerprint(channelId);
        return new InsertResult(savedItem, newFingerprint);
    }

    private void validateInsertIndex(int index, int totalCount) {
        if (index < 0) {
            throw new InvalidIndexException("Index must be non-negative");
        }
        if (index > totalCount) {
            throw new InvalidIndexException("Index " + index + " is out of range. Valid range is 0 to " + totalCount);
        }
    }

    private void validatePagination(int offset, int limit) {
        if (offset < 0) {
            throw new InvalidPaginationException("Offset must be non-negative");
        }
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new InvalidPaginationException("Limit must be between " + MIN_LIMIT + " and " + MAX_LIMIT);
        }
    }

    /**
     * Computes the fingerprint for a channel's playlist.
     * Fingerprint is SHA-256 of "0:itemId0|1:itemId1|..." string.
     */
    private String computeFingerprint(String channelId) {
        List<PlaylistItem> allItems = playlistItemPort.findAllByChannelIdOrderedByIndex(channelId);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < allItems.size(); i++) {
            if (i > 0) {
                sb.append("|");
            }
            PlaylistItem item = allItems.get(i);
            sb.append(item.getIndex()).append(":").append(item.getId());
        }

        return sha256Hex(sb.toString());
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
