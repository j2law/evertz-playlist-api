package com.evertz.playlist.infrastructure.api.controller;

import com.evertz.playlist.application.service.PlaylistService;
import com.evertz.playlist.infrastructure.api.dto.PageInfo;
import com.evertz.playlist.infrastructure.api.dto.PlaylistItemResponse;
import com.evertz.playlist.infrastructure.api.dto.PlaylistResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for playlist operations.
 */
@RestController
@RequestMapping("/api/channels/{channelId}/playlist")
public class PlaylistController {

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @GetMapping("/items")
    public ResponseEntity<PlaylistResponse> getItems(
            @PathVariable String channelId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        PlaylistService.PlaylistResult result = playlistService.getItems(channelId, offset, limit);

        List<PlaylistItemResponse> itemResponses = result.items().stream()
                .map(item -> new PlaylistItemResponse(item.getId(), item.getIndex(), item.getTitle()))
                .toList();

        boolean hasMore = offset + result.items().size() < result.totalCount();
        Integer nextOffset = hasMore ? offset + limit : null;
        PageInfo pageInfo = new PageInfo(limit, offset, nextOffset, hasMore);

        return ResponseEntity.ok(new PlaylistResponse(
                itemResponses,
                pageInfo,
                result.totalCount(),
                result.serverFingerprint()
        ));
    }
}
