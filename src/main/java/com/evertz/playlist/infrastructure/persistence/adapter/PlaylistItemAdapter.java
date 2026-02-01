package com.evertz.playlist.infrastructure.persistence.adapter;

import com.evertz.playlist.application.port.PlaylistItemPort;
import com.evertz.playlist.core.model.PlaylistItem;
import com.evertz.playlist.infrastructure.persistence.dao.PlaylistItemDao;
import com.evertz.playlist.infrastructure.persistence.repository.PlaylistItemJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Database operation implementations, interacts with JPA repository.
 * Adapter implementing PlaylistItemPort using Spring Data JPA.
 * Maps between JPA DAOs and core domain entities.
 */
@Component
public class PlaylistItemAdapter implements PlaylistItemPort {

    private final PlaylistItemJpaRepository jpaRepository;

    public PlaylistItemAdapter(PlaylistItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<PlaylistItem> findByChannelId(String channelId, int offset, int limit) {
        return jpaRepository.findByChannelIdWithPagination(channelId, offset, limit)
                .stream()
                .map(this::toCoreEntity)
                .toList();
    }

    @Override
    public List<PlaylistItem> findAllByChannelIdOrderedByIndex(String channelId) {
        return jpaRepository.findByChannelIdOrderByIndexAsc(channelId)
                .stream()
                .map(this::toCoreEntity)
                .toList();
    }

    @Override
    public int countByChannelId(String channelId) {
        return jpaRepository.countByChannelId(channelId);
    }

    @Override
    public PlaylistItem save(PlaylistItem item) {
        PlaylistItemDao dao = toDao(item);
        PlaylistItemDao savedDao = jpaRepository.save(dao);
        return toCoreEntity(savedDao);
    }

    @Override
    public void shiftIndexes(String channelId, int fromIndex, int shiftAmount) {
        jpaRepository.shiftIndexes(channelId, fromIndex, shiftAmount);
    }

    private PlaylistItem toCoreEntity(PlaylistItemDao dao) {
        return new PlaylistItem(
                dao.getId(),
                dao.getChannelId(),
                dao.getTitle(),
                dao.getIndex()
        );
    }

    private PlaylistItemDao toDao(PlaylistItem item) {
        return new PlaylistItemDao(
                item.getId(),
                item.getChannelId(),
                item.getTitle(),
                item.getIndex()
        );
    }
}
