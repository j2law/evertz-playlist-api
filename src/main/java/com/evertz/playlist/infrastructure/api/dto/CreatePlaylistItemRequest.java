package com.evertz.playlist.infrastructure.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new playlist item.
 */
public record CreatePlaylistItemRequest(
        @NotBlank(message = "Title must not be empty or blank")
        String title,
        int index,
        @NotNull(message = "clientFingerprint is required")
        String clientFingerprint
) {}
