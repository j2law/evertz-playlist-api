package com.evertz.playlist.infrastructure.persistence.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * JPA data access object for playlist items.
 * Separate from the core domain entity to keep persistence concerns isolated.
 */
@Entity
@Table(
        name = "playlist_items",
        indexes = {
                @Index(name = "idx_channel_index", columnList = "channel_id, item_index"),
                @Index(name = "idx_channel_id", columnList = "channel_id")
        }
)
public class PlaylistItemDao {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "channel_id", nullable = false, length = 100)
    private String channelId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "item_index", nullable = false)
    private int index;

    public PlaylistItemDao() {
    }

    public PlaylistItemDao(String id, String channelId, String title, int index) {
        this.id = id;
        this.channelId = channelId;
        this.title = title;
        this.index = index;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
}
