package com.evertz.playlist.infrastructure.api.controller;

import com.evertz.playlist.application.service.PlaylistService;
import com.evertz.playlist.infrastructure.api.dto.CreatePlaylistItemRequest;
import com.evertz.playlist.infrastructure.api.dto.DeleteItemRequest;
import com.evertz.playlist.infrastructure.api.dto.DeleteItemResponse;
import com.evertz.playlist.infrastructure.api.dto.InsertItemResponse;
import com.evertz.playlist.infrastructure.api.dto.MoveItemRequest;
import com.evertz.playlist.infrastructure.api.dto.MoveItemResponse;
import com.evertz.playlist.infrastructure.api.dto.PageInfo;
import com.evertz.playlist.infrastructure.api.dto.PlaylistItemResponse;
import com.evertz.playlist.infrastructure.api.dto.PlaylistResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/items")
    public ResponseEntity<InsertItemResponse> insertItem(
            @PathVariable String channelId,
            @RequestBody CreatePlaylistItemRequest request
    ) {
        PlaylistService.InsertResult result = playlistService.insertItem(
                channelId,
                request.title(),
                request.index(),
                request.clientFingerprint()
        );

        PlaylistItemResponse itemResponse = new PlaylistItemResponse(
                result.item().getId(),
                result.item().getIndex(),
                result.item().getTitle()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new InsertItemResponse(itemResponse, result.serverFingerprint()));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<DeleteItemResponse> deleteItem(
            @PathVariable String channelId,
            @PathVariable String itemId,
            @RequestBody DeleteItemRequest request
    ) {
        PlaylistService.DeleteResult result = playlistService.deleteItem(
                channelId,
                itemId,
                request.clientFingerprint()
        );

        return ResponseEntity.ok(new DeleteItemResponse(result.serverFingerprint()));
    }

    @PostMapping("/items/{itemId}/move")
    public ResponseEntity<MoveItemResponse> moveItem(
            @PathVariable String channelId,
            @PathVariable String itemId,
            @RequestBody MoveItemRequest request
    ) {
        PlaylistService.MoveResult result = playlistService.moveItem(
                channelId,
                itemId,
                request.newIndex(),
                request.clientFingerprint()
        );

        PlaylistItemResponse itemResponse = new PlaylistItemResponse(
                result.item().getId(),
                result.item().getIndex(),
                result.item().getTitle()
        );

        return ResponseEntity.ok(new MoveItemResponse(itemResponse, result.serverFingerprint()));
    }
}
