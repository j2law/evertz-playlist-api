package com.evertz.playlist.infrastructure.persistence.repository;

import com.evertz.playlist.infrastructure.persistence.dao.PlaylistItemDao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for playlist items.
 */
@Repository
public interface PlaylistItemJpaRepository extends JpaRepository<PlaylistItemDao, String> {

    /**
     * Finds playlist items with pagination using native query for proper offset/limit support.
     */
    @Query(value = "SELECT * FROM playlist_items WHERE channel_id = :channelId ORDER BY item_index ASC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<PlaylistItemDao> findByChannelIdWithPagination(
            @Param("channelId") String channelId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * Finds all items for a channel ordered by index.
     */
    List<PlaylistItemDao> findByChannelIdOrderByIndexAsc(String channelId);

    /**
     * Counts items in a channel's playlist.
     */
    int countByChannelId(String channelId);
}
