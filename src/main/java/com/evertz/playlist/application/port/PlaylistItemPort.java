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

    /**
     * Saves a playlist item.
     *
     * @param item the item to save
     * @return the saved item
     */
    PlaylistItem save(PlaylistItem item);

    /**
     * Shifts indexes of items at or after the given index by the specified amount.
     * Used when inserting or deleting items.
     *
     * @param channelId  the channel identifier
     * @param fromIndex  the starting index (inclusive)
     * @param shiftAmount the amount to shift (positive for insert, negative for delete)
     */
    void shiftIndexes(String channelId, int fromIndex, int shiftAmount);

    /**
     * Finds a playlist item by its ID.
     *
     * @param itemId the item identifier
     * @return the playlist item, or null if not found
     */
    PlaylistItem findById(String itemId);

    /**
     * Deletes a playlist item by its ID.
     *
     * @param itemId the item identifier
     */
    void deleteById(String itemId);

    /**
     * Shifts indexes of items in a range by the specified amount.
     * Used when moving items.
     *
     * @param channelId   the channel identifier
     * @param fromIndex   the starting index (inclusive)
     * @param toIndex     the ending index (inclusive)
     * @param shiftAmount the amount to shift
     */
    void shiftIndexesInRange(String channelId, int fromIndex, int toIndex, int shiftAmount);

    /**
     * Updates the index of a specific item.
     *
     * @param itemId   the item identifier
     * @param newIndex the new index
     */
    void updateIndex(String itemId, int newIndex);
}
