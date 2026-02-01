package com.evertz.playlist.core.model;

import java.util.Objects;

/**
 * Domain entity representing an item in a channel's playlist.
 * Pure domain object with no framework dependencies.
 */
public class PlaylistItem {

    private final String id;
    private final String channelId;
    private final String title;
    private int index;

    public PlaylistItem(String id, String channelId, String title, int index) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.channelId = Objects.requireNonNull(channelId, "channelId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.index = index;
    }

    public String getId() {
        return id;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getTitle() {
        return title;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaylistItem that = (PlaylistItem) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
