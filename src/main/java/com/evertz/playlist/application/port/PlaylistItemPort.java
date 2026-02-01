package com.evertz.playlist.application.port;

import com.evertz.playlist.core.model.PlaylistItem;

import java.util.List;

/**
 * Persistence interface for playlist items. Database plugs into here for the actual implementation.
 * Port interface for playlist item persistence operations.
 * This abstraction allows the application layer to remain independent of the persistence implementation.
 */
public interface PlaylistItemPort {

    /**
     * Finds playlist items for a channel with pagination.
     *
     * @param channelId the channel identifier
     * @param offset    the number of items to skip (0-based)
     * @param limit     the maximum number of items to return
     * @return list of playlist items ordered by index
     */
    List<PlaylistItem> findByChannelId(String channelId, int offset, int limit);

    /**
     * Finds all playlist items for a channel ordered by index.
     * Used for fingerprint computation.
     *
     * @param channelId the channel identifier
     * @return all playlist items ordered by index
     */
    List<PlaylistItem> findAllByChannelIdOrderedByIndex(String channelId);

    /**
     * Counts the total number of items in a channel's playlist.
     *
     * @param channelId the channel identifier
     * @return the total count
     */
    int countByChannelId(String channelId);
}
