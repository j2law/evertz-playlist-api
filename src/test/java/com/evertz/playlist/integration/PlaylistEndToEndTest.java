package com.evertz.playlist.integration;

import com.evertz.playlist.infrastructure.api.dto.CreatePlaylistItemRequest;
import com.evertz.playlist.infrastructure.api.dto.DeleteItemRequest;
import com.evertz.playlist.infrastructure.api.dto.DeleteItemResponse;
import com.evertz.playlist.infrastructure.api.dto.ErrorResponse;
import com.evertz.playlist.infrastructure.api.dto.FingerprintMismatchResponse;
import com.evertz.playlist.infrastructure.api.dto.InsertItemResponse;
import com.evertz.playlist.infrastructure.api.dto.MoveItemRequest;
import com.evertz.playlist.infrastructure.api.dto.MoveItemResponse;
import com.evertz.playlist.infrastructure.api.dto.PlaylistItemResponse;
import com.evertz.playlist.infrastructure.api.dto.PlaylistResponse;
import com.evertz.playlist.infrastructure.api.dto.SyncCheckRequest;
import com.evertz.playlist.infrastructure.api.dto.SyncCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for the Playlist API.
 * Tests exercise real HTTP endpoints with the full Spring context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PlaylistEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private String channelId;

    @BeforeEach
    void setUp() {
        // Use a unique channel ID for each test to ensure isolation
        channelId = "TEST_CHANNEL_" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private String getBaseUrl() {
        return "/api/channels/" + channelId + "/playlist";
    }

    private PlaylistResponse getPlaylist() {
        return restTemplate.getForObject(getBaseUrl() + "/items?limit=100", PlaylistResponse.class);
    }

    private String getServerFingerprint() {
        return getPlaylist().serverFingerprint();
    }

    private InsertItemResponse insertItem(String title, int index, String fingerprint) {
        var request = new CreatePlaylistItemRequest(title, index, fingerprint);
        ResponseEntity<InsertItemResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/items", request, InsertItemResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private DeleteItemResponse deleteItem(String itemId, String fingerprint) {
        var request = new DeleteItemRequest(fingerprint);
        ResponseEntity<DeleteItemResponse> response = restTemplate.exchange(
                getBaseUrl() + "/items/" + itemId,
                HttpMethod.DELETE,
                new HttpEntity<>(request),
                DeleteItemResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private MoveItemResponse moveItem(String itemId, int newIndex, String fingerprint) {
        var request = new MoveItemRequest(newIndex, fingerprint);
        ResponseEntity<MoveItemResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/items/" + itemId + "/move", request, MoveItemResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    /**
     * Verifies that the playlist maintains healthy invariants:
     * - Indexes are contiguous (0, 1, 2, ...)
     * - No duplicate indexes
     * - No gaps
     * - Each item appears exactly once
     */
    private void assertPlaylistHealthy() {
        PlaylistResponse response = getPlaylist();
        List<PlaylistItemResponse> items = response.items();

        // Assert count matches totalCount
        assertThat(items).hasSize(response.totalCount());

        // Assert contiguous indexes starting from 0
        for (int i = 0; i < items.size(); i++) {
            assertThat(items.get(i).index())
                    .withFailMessage("Expected index %d but found %d at position %d", i, items.get(i).index(), i)
                    .isEqualTo(i);
        }

        // Assert no duplicate indexes
        List<Integer> indexes = items.stream().map(PlaylistItemResponse::index).toList();
        assertThat(indexes).doesNotHaveDuplicates();

        // Assert no duplicate item IDs
        List<String> itemIds = items.stream().map(PlaylistItemResponse::itemId).toList();
        assertThat(itemIds).doesNotHaveDuplicates();
    }

    /**
     * Creates a playlist with the specified number of items.
     * Returns the list of item IDs in order.
     */
    private List<String> createPlaylistWithItems(int count) {
        List<String> itemIds = new ArrayList<>();
        String fingerprint = getServerFingerprint();

        for (int i = 0; i < count; i++) {
            InsertItemResponse response = insertItem("Item " + i, i, fingerprint);
            itemIds.add(response.item().itemId());
            fingerprint = response.serverFingerprint();
        }

        return itemIds;
    }

    // ========================================================================
    // 1. Ordering Invariants Tests
    // ========================================================================

    @Nested
    @DisplayName("1. Ordering Invariants")
    class OrderingInvariantsTests {

        @Test
        @DisplayName("Playlist is healthy after multiple inserts")
        void playlistHealthyAfterInserts() {
            createPlaylistWithItems(10);
            assertPlaylistHealthy();
        }

        @Test
        @DisplayName("Playlist is healthy after insert then delete")
        void playlistHealthyAfterInsertAndDelete() {
            List<String> itemIds = createPlaylistWithItems(5);

            // Delete middle item
            String fingerprint = getServerFingerprint();
            deleteItem(itemIds.get(2), fingerprint);

            assertPlaylistHealthy();
            assertThat(getPlaylist().items()).hasSize(4);
        }

        @Test
        @DisplayName("Playlist is healthy after insert, delete, and move")
        void playlistHealthyAfterMixedOperations() {
            List<String> itemIds = createPlaylistWithItems(5);
            String fingerprint = getServerFingerprint();

            // Delete item at index 1
            fingerprint = deleteItem(itemIds.get(1), fingerprint).serverFingerprint();
            assertPlaylistHealthy();

            // Insert new item at index 2
            fingerprint = insertItem("New Item", 2, fingerprint).serverFingerprint();
            assertPlaylistHealthy();

            // Move first item to end
            PlaylistResponse playlist = getPlaylist();
            String firstItemId = playlist.items().get(0).itemId();
            moveItem(firstItemId, playlist.items().size() - 1, fingerprint);
            assertPlaylistHealthy();
        }

        @Test
        @DisplayName("Empty playlist has valid state")
        void emptyPlaylistIsHealthy() {
            assertPlaylistHealthy();
            PlaylistResponse response = getPlaylist();
            assertThat(response.items()).isEmpty();
            assertThat(response.totalCount()).isZero();
        }
    }

    // ========================================================================
    // 2. Insert Scenarios Tests
    // ========================================================================

    @Nested
    @DisplayName("2. Insert Scenarios")
    class InsertScenariosTests {

        @Test
        @DisplayName("Insert at start (index 0)")
        void insertAtStart() {
            // Create initial items
            createPlaylistWithItems(3);
            String fingerprint = getServerFingerprint();

            // Insert at start
            InsertItemResponse response = insertItem("First Item", 0, fingerprint);

            assertThat(response.item().index()).isZero();
            assertThat(response.item().title()).isEqualTo("First Item");

            PlaylistResponse playlist = getPlaylist();
            assertThat(playlist.items().get(0).title()).isEqualTo("First Item");
            assertThat(playlist.items()).hasSize(4);
            assertPlaylistHealthy();
        }

        @Test
        @DisplayName("Insert in middle")
        void insertInMiddle() {
            createPlaylistWithItems(4);
            String fingerprint = getServerFingerprint();

            // Insert at index 2 (middle)
            InsertItemResponse response = insertItem("Middle Item", 2, fingerprint);

            assertThat(response.item().index()).isEqualTo(2);

            PlaylistResponse playlist = getPlaylist();
            assertThat(playlist.items().get(2).title()).isEqualTo("Middle Item");
            assertThat(playlist.items()).hasSize(5);
            assertPlaylistHealthy();
        }

        @Test
        @DisplayName("Insert at end")
        void insertAtEnd() {
            createPlaylistWithItems(3);
            String fingerprint = getServerFingerprint();

            // Insert at index 3 (end, after last item)
            InsertItemResponse response = insertItem("Last Item", 3, fingerprint);

            assertThat(response.item().index()).isEqualTo(3);

            PlaylistResponse playlist = getPlaylist();
            assertThat(playlist.items().get(3).title()).isEqualTo("Last Item");
            assertThat(playlist.items()).hasSize(4);
            assertPlaylistHealthy();
        }

        @Test
        @DisplayName("Insert at out-of-range index returns 400")
        void insertOutOfRange() {
            createPlaylistWithItems(3);
            String fingerprint = getServerFingerprint();

            var request = new CreatePlaylistItemRequest("Bad Item", 10, fingerprint);
            ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                    getBaseUrl() + "/items", request, ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().errorCode()).isEqualTo("INVALID_INDEX");
        }

        @Test
        @DisplayName("Insert at negative index returns 400")
        void insertNegativeIndex() {
            String fingerprint = getServerFingerprint();

            var request = new CreatePlaylistItemRequest("Bad Item", -1, fingerprint);
            ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                    getBaseUrl() + "/items", request, ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().errorCode()).isEqualTo("INVALID_INDEX");
        }

        @Test
        @DisplayName("Insert into empty playlist at index 0")
        void insertIntoEmptyPlaylist() {
            String fingerprint = getServerFingerprint();

            InsertItemResponse response = insertItem("First Item", 0, fingerprint);

            assertThat(response.item().index()).isZero();
            assertThat(getPlaylist().items()).hasSize(1);
            assertPlaylistHealthy();
        }
    }

    // ========================================================================
    // 3. Delete Scenarios Tests
    // ========================================================================

    @Nested
    @DisplayName("3. Delete Scenarios")
    class DeleteScenariosTests {

        @Test
        @DisplayName("Delete first item")
        void deleteFirst() {
            List<String> itemIds = createPlaylistWithItems(4);
            String fingerprint = getServerFingerprint();

            deleteItem(itemIds.get(0), fingerprint);

            PlaylistResponse playlist = getPlaylist();
            assertThat(playlist.items()).hasSize(3);
            // Former second item should now be first with index 0
            assertThat(playlist.items().get(0).title()).isEqualTo("Item 1");
            assertPlaylistHealthy();
        }

        @Test
        @DisplayName("Delete middle item")
        void deleteMiddle() {
            List<String> itemIds = createPlaylistWithItems(5);
            String fingerprint = getServerFingerprint();

            deleteItem(itemIds.get(2), fingerprint);

            PlaylistResponse playlist = getPlaylist();
            assertThat(playlist.items()).hasSize(4);
            // Check gap was closed
            assertThat(playlist.items().get(2).title()).isEqualTo("Item 3");
            assertPlaylistHealthy();
        }

        @Test
        @DisplayName("Delete last item")
        void deleteLast() {
            List<String> itemIds = createPlaylistWithItems(4);
            String fingerprint = getServerFingerprint();

            deleteItem(itemIds.get(3), fingerprint);

            PlaylistResponse playlist = getPlaylist();
            assertThat(playlist.items()).hasSize(3);
            assertPlaylistHealthy();
        }

        @Test
        @DisplayName("Delete non-existent item returns 404")
        void deleteNonExistent() {
            createPlaylistWithItems(3);
            String fingerprint = getServerFingerprint();

            var request = new DeleteItemRequest(fingerprint);
            ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                    getBaseUrl() + "/items/non-existent-id",
                    HttpMethod.DELETE,
                    new HttpEntity<>(request),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().errorCode()).isEqualTo("NOT_FOUND");
        }

        @Test
        @DisplayName("Delete item from different channel returns 404")
        void deleteFromWrongChannel() {
            List<String> itemIds = createPlaylistWithItems(3);

            // Try to delete from a different channel
            String otherChannelUrl = "/api/channels/OTHER_CHANNEL/playlist";
            PlaylistResponse otherPlaylist = restTemplate.getForObject(
                    otherChannelUrl + "/items?limit=100", PlaylistResponse.class);

            var request = new DeleteItemRequest(otherPlaylist.serverFingerprint());
            ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                    otherChannelUrl + "/items/" + itemIds.get(0),
                    HttpMethod.DELETE,
                    new HttpEntity<>(request),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Delete all items leaves empty playlist")
        void deleteAllItems() {
            List<String> itemIds = createPlaylistWithItems(3);
            String fingerprint = getServerFingerprint();

            for (String itemId : itemIds) {
                fingerprint = deleteItem(itemId, fingerprint).serverFingerprint();
            }

            PlaylistResponse playlist = getPlaylist();
            assertThat(playlist.items()).isEmpty();
            assertThat(playlist.totalCount()).isZero();
            assertPlaylistHealthy();
        }
    }

    // ========================================================================
    // 4. Move Scenarios Tests
    // ========================================================================

    @Nested
    @DisplayName("4. Move Scenarios")
    class MoveScenariosTests {

        @Test
        @DisplayName("Move forward (low index to high index)")
        void moveForward() {
            List<String> itemIds = createPlaylistWithItems(5);
            String fingerprint = getServerFingerprint();

            // Move item from index 0 to index 3
            MoveItemResponse response = moveItem(itemIds.get(0), 3, fingerprint);

            assertThat(response.item().index()).isEqualTo(3);

            PlaylistResponse playlist = getPlaylist();
            // Original Item 0 should now be at index 3
            assertThat(playlist.items().get(3).itemId()).isEqualTo(itemIds.get(0));
            // Items 1, 2, 3 should have shifted down
            assertThat(playlist.items().get(0).itemId()).isEqualTo(itemIds.get(1));
            assertThat(playlist.items().get(1).itemId()).isEqualTo(itemIds.get(2));
            assertThat(playlist.items().get(2).itemId()).isEqualTo(itemIds.get(3));
            assertPlaylistHealthy();
        }

        @Test
        @DisplayName("Move backward (high index to low index)")
        void moveBackward() {
            List<String> itemIds = createPlaylistWithItems(5);
            String fingerprint = getServerFingerprint();

            // Move item from index 4 to index 1
            MoveItemResponse response = moveItem(itemIds.get(4), 1, fingerprint);

            assertThat(response.item().index()).isEqualTo(1);

            PlaylistResponse playlist = getPlaylist();
            // Original Item 4 should now be at index 1
            assertThat(playlist.items().get(1).itemId()).isEqualTo(itemIds.get(4));
            // Items 1, 2, 3 should have shifted up
            assertThat(playlist.items().get(2).itemId()).isEqualTo(itemIds.get(1));
            assertThat(playlist.items().get(3).itemId()).isEqualTo(itemIds.get(2));
            assertThat(playlist.items().get(4).itemId()).isEqualTo(itemIds.get(3));
            assertPlaylistHealthy();
        }

        @Test
        @DisplayName("Move to same index (no-op)")
        void moveToSameIndex() {
            List<String> itemIds = createPlaylistWithItems(5);
            String fingerprint = getServerFingerprint();

            // Move item from index 2 to index 2 (no-op)
            MoveItemResponse response = moveItem(itemIds.get(2), 2, fingerprint);

            assertThat(response.item().index()).isEqualTo(2);
            assertThat(response.item().itemId()).isEqualTo(itemIds.get(2));

            // Playlist should be unchanged
            PlaylistResponse playlist = getPlaylist();
            for (int i = 0; i < itemIds.size(); i++) {
                assertThat(playlist.items().get(i).itemId()).isEqualTo(itemIds.get(i));
            }
            assertPlaylistHealthy();
        }

        @Test
        @DisplayName("Move to out-of-range index returns 400")
        void moveOutOfRange() {
            List<String> itemIds = createPlaylistWithItems(5);
            String fingerprint = getServerFingerprint();

            var request = new MoveItemRequest(10, fingerprint);
            ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                    getBaseUrl() + "/items/" + itemIds.get(0) + "/move",
                    request, ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().errorCode()).isEqualTo("INVALID_INDEX");
        }

        @Test
        @DisplayName("Move to negative index returns 400")
        void moveToNegativeIndex() {
            List<String> itemIds = createPlaylistWithItems(3);
            String fingerprint = getServerFingerprint();

            var request = new MoveItemRequest(-1, fingerprint);
            ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                    getBaseUrl() + "/items/" + itemIds.get(0) + "/move",
                    request, ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().errorCode()).isEqualTo("INVALID_INDEX");
        }

        @Test
        @DisplayName("Move non-existent item returns 404")
        void moveNonExistent() {
            createPlaylistWithItems(3);
            String fingerprint = getServerFingerprint();

            var request = new MoveItemRequest(1, fingerprint);
            ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                    getBaseUrl() + "/items/non-existent-id/move",
                    request, ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().errorCode()).isEqualTo("NOT_FOUND");
        }

        @Test
        @DisplayName("Move first to last")
        void moveFirstToLast() {
            List<String> itemIds = createPlaylistWithItems(4);
            String fingerprint = getServerFingerprint();

            moveItem(itemIds.get(0), 3, fingerprint);

            PlaylistResponse playlist = getPlaylist();
            assertThat(playlist.items().get(3).itemId()).isEqualTo(itemIds.get(0));
            assertPlaylistHealthy();
        }

        @Test
        @DisplayName("Move last to first")
        void moveLastToFirst() {
            List<String> itemIds = createPlaylistWithItems(4);
            String fingerprint = getServerFingerprint();

            moveItem(itemIds.get(3), 0, fingerprint);

            PlaylistResponse playlist = getPlaylist();
            assertThat(playlist.items().get(0).itemId()).isEqualTo(itemIds.get(3));
            assertPlaylistHealthy();
        }
    }

    // ========================================================================
    // 5. Pagination Tests
    // ========================================================================

    @Nested
    @DisplayName("5. Pagination")
    class PaginationTests {

        @Test
        @DisplayName("List items across multiple pages returns all items exactly once")
        void paginationReturnsAllItemsOnce() {
            // Create 25 items
            List<String> itemIds = createPlaylistWithItems(25);

            // Fetch all pages with limit 10
            Set<String> seenItemIds = new HashSet<>();
            int offset = 0;
            int limit = 10;
            int totalFetched = 0;

            while (true) {
                PlaylistResponse response = restTemplate.getForObject(
                        getBaseUrl() + "/items?offset=" + offset + "&limit=" + limit,
                        PlaylistResponse.class);

                for (PlaylistItemResponse item : response.items()) {
                    // Each item should only be seen once
                    assertThat(seenItemIds.add(item.itemId()))
                            .withFailMessage("Item %s appeared more than once", item.itemId())
                            .isTrue();
                    totalFetched++;
                }

                if (!response.page().hasMore()) {
                    break;
                }
                offset = response.page().nextOffset();
            }

            // All items should have been fetched
            assertThat(totalFetched).isEqualTo(25);
            assertThat(seenItemIds).hasSize(25);
            assertThat(seenItemIds).containsExactlyInAnyOrderElementsOf(itemIds);
        }

        @Test
        @DisplayName("Ordering is deterministic across pages")
        void paginationOrderIsDeterministic() {
            createPlaylistWithItems(15);

            // Fetch first page
            PlaylistResponse page1 = restTemplate.getForObject(
                    getBaseUrl() + "/items?offset=0&limit=5", PlaylistResponse.class);

            // Fetch second page
            PlaylistResponse page2 = restTemplate.getForObject(
                    getBaseUrl() + "/items?offset=5&limit=5", PlaylistResponse.class);

            // Fetch third page
            PlaylistResponse page3 = restTemplate.getForObject(
                    getBaseUrl() + "/items?offset=10&limit=5", PlaylistResponse.class);

            // Verify ordering: items should be in index order
            assertThat(page1.items().get(0).index()).isEqualTo(0);
            assertThat(page1.items().get(4).index()).isEqualTo(4);
            assertThat(page2.items().get(0).index()).isEqualTo(5);
            assertThat(page2.items().get(4).index()).isEqualTo(9);
            assertThat(page3.items().get(0).index()).isEqualTo(10);
            assertThat(page3.items().get(4).index()).isEqualTo(14);
        }

        @Test
        @DisplayName("nextOffset is correct when more pages exist")
        void nextOffsetCorrectWhenMorePages() {
            createPlaylistWithItems(20);

            PlaylistResponse response = restTemplate.getForObject(
                    getBaseUrl() + "/items?offset=0&limit=10", PlaylistResponse.class);

            assertThat(response.page().hasMore()).isTrue();
            assertThat(response.page().nextOffset()).isEqualTo(10);
        }

        @Test
        @DisplayName("nextOffset is null when no more pages")
        void nextOffsetNullWhenNoMorePages() {
            createPlaylistWithItems(5);

            PlaylistResponse response = restTemplate.getForObject(
                    getBaseUrl() + "/items?offset=0&limit=10", PlaylistResponse.class);

            assertThat(response.page().hasMore()).isFalse();
            assertThat(response.page().nextOffset()).isNull();
        }

        @Test
        @DisplayName("Empty page when offset exceeds total")
        void emptyPageWhenOffsetExceedsTotal() {
            createPlaylistWithItems(5);

            PlaylistResponse response = restTemplate.getForObject(
                    getBaseUrl() + "/items?offset=100&limit=10", PlaylistResponse.class);

            assertThat(response.items()).isEmpty();
            assertThat(response.page().hasMore()).isFalse();
            assertThat(response.totalCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Invalid pagination parameters return 400")
        void invalidPaginationReturns400() {
            ResponseEntity<ErrorResponse> negativeOffset = restTemplate.getForEntity(
                    getBaseUrl() + "/items?offset=-1", ErrorResponse.class);
            assertThat(negativeOffset.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(negativeOffset.getBody().errorCode()).isEqualTo("INVALID_PAGINATION");

            ResponseEntity<ErrorResponse> zeroLimit = restTemplate.getForEntity(
                    getBaseUrl() + "/items?limit=0", ErrorResponse.class);
            assertThat(zeroLimit.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ResponseEntity<ErrorResponse> tooHighLimit = restTemplate.getForEntity(
                    getBaseUrl() + "/items?limit=101", ErrorResponse.class);
            assertThat(tooHighLimit.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Fingerprint is consistent across pages")
        void fingerprintConsistentAcrossPages() {
            createPlaylistWithItems(20);

            PlaylistResponse page1 = restTemplate.getForObject(
                    getBaseUrl() + "/items?offset=0&limit=10", PlaylistResponse.class);
            PlaylistResponse page2 = restTemplate.getForObject(
                    getBaseUrl() + "/items?offset=10&limit=10", PlaylistResponse.class);

            // Same fingerprint since playlist hasn't changed
            assertThat(page1.serverFingerprint()).isEqualTo(page2.serverFingerprint());
        }
    }

    // ========================================================================
    // 6. Fingerprint Mismatch Tests
    // ========================================================================

    @Nested
    @DisplayName("6. Fingerprint Mismatch")
    class FingerprintMismatchTests {

        @Test
        @DisplayName("Successful mutation with matching fingerprint")
        void successWithMatchingFingerprint() {
            String fingerprint = getServerFingerprint();

            // Insert should succeed with matching fingerprint
            InsertItemResponse response = insertItem("Test Item", 0, fingerprint);

            assertThat(response.item()).isNotNull();
            assertThat(response.serverFingerprint()).isNotNull();
            assertThat(response.serverFingerprint()).isNotEqualTo(fingerprint); // Changed after mutation
        }

        @Test
        @DisplayName("Sync-check returns 200 with matching fingerprint")
        void syncCheckMatchReturns200() {
            createPlaylistWithItems(3);
            String fingerprint = getServerFingerprint();

            var request = new SyncCheckRequest(fingerprint);
            ResponseEntity<SyncCheckResponse> response = restTemplate.postForEntity(
                    getBaseUrl() + "/sync-check", request, SyncCheckResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().serverFingerprint()).isEqualTo(fingerprint);
        }

        @Test
        @DisplayName("Sync-check returns 409 with stale fingerprint")
        void syncCheckMismatchReturns409() {
            createPlaylistWithItems(3);

            var request = new SyncCheckRequest("stale_fingerprint");
            ResponseEntity<FingerprintMismatchResponse> response = restTemplate.postForEntity(
                    getBaseUrl() + "/sync-check", request, FingerprintMismatchResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().errorCode()).isEqualTo("PLAYLIST_FINGERPRINT_MISMATCH");
            assertThat(response.getBody().serverFingerprint()).isNotNull();
        }

        @Test
        @DisplayName("Insert returns 409 with stale fingerprint")
        void insertMismatchReturns409() {
            createPlaylistWithItems(3);

            var request = new CreatePlaylistItemRequest("New Item", 0, "stale_fingerprint");
            ResponseEntity<FingerprintMismatchResponse> response = restTemplate.postForEntity(
                    getBaseUrl() + "/items", request, FingerprintMismatchResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().errorCode()).isEqualTo("PLAYLIST_FINGERPRINT_MISMATCH");
            assertThat(response.getBody().serverFingerprint()).isNotNull();
        }

        @Test
        @DisplayName("Delete returns 409 with stale fingerprint")
        void deleteMismatchReturns409() {
            List<String> itemIds = createPlaylistWithItems(3);

            var request = new DeleteItemRequest("stale_fingerprint");
            ResponseEntity<FingerprintMismatchResponse> response = restTemplate.exchange(
                    getBaseUrl() + "/items/" + itemIds.get(0),
                    HttpMethod.DELETE,
                    new HttpEntity<>(request),
                    FingerprintMismatchResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().errorCode()).isEqualTo("PLAYLIST_FINGERPRINT_MISMATCH");
            assertThat(response.getBody().serverFingerprint()).isNotNull();
        }

        @Test
        @DisplayName("Move returns 409 with stale fingerprint")
        void moveMismatchReturns409() {
            List<String> itemIds = createPlaylistWithItems(3);

            var request = new MoveItemRequest(2, "stale_fingerprint");
            ResponseEntity<FingerprintMismatchResponse> response = restTemplate.postForEntity(
                    getBaseUrl() + "/items/" + itemIds.get(0) + "/move",
                    request, FingerprintMismatchResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().errorCode()).isEqualTo("PLAYLIST_FINGERPRINT_MISMATCH");
            assertThat(response.getBody().serverFingerprint()).isNotNull();
        }

        @Test
        @DisplayName("Fingerprint changes after each mutation")
        void fingerprintChangesAfterMutation() {
            String fp1 = getServerFingerprint();

            // Insert changes fingerprint
            InsertItemResponse insertResponse = insertItem("Item 1", 0, fp1);
            String fp2 = insertResponse.serverFingerprint();
            assertThat(fp2).isNotEqualTo(fp1);

            // Another insert changes fingerprint again
            InsertItemResponse insertResponse2 = insertItem("Item 2", 1, fp2);
            String fp3 = insertResponse2.serverFingerprint();
            assertThat(fp3).isNotEqualTo(fp2);

            // Delete changes fingerprint
            String itemId = insertResponse.item().itemId();
            DeleteItemResponse deleteResponse = deleteItem(itemId, fp3);
            String fp4 = deleteResponse.serverFingerprint();
            assertThat(fp4).isNotEqualTo(fp3);
        }

        @Test
        @DisplayName("409 response includes current server fingerprint for recovery")
        void mismatchResponseIncludesCurrentFingerprint() {
            createPlaylistWithItems(3);
            String currentFingerprint = getServerFingerprint();

            var request = new SyncCheckRequest("stale_fingerprint");
            ResponseEntity<FingerprintMismatchResponse> response = restTemplate.postForEntity(
                    getBaseUrl() + "/sync-check", request, FingerprintMismatchResponse.class);

            // The returned fingerprint should match the current server fingerprint
            assertThat(response.getBody().serverFingerprint()).isEqualTo(currentFingerprint);
        }

        @Test
        @DisplayName("Client can recover from stale fingerprint using 409 response")
        void clientCanRecoverFromStaleFingerprint() {
            createPlaylistWithItems(3);

            // Attempt insert with stale fingerprint
            var badRequest = new CreatePlaylistItemRequest("New Item", 0, "stale_fingerprint");
            ResponseEntity<FingerprintMismatchResponse> failedResponse = restTemplate.postForEntity(
                    getBaseUrl() + "/items", badRequest, FingerprintMismatchResponse.class);

            assertThat(failedResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            // Use the fingerprint from 409 response to retry
            String recoveredFingerprint = failedResponse.getBody().serverFingerprint();
            var goodRequest = new CreatePlaylistItemRequest("New Item", 0, recoveredFingerprint);
            ResponseEntity<InsertItemResponse> successResponse = restTemplate.postForEntity(
                    getBaseUrl() + "/items", goodRequest, InsertItemResponse.class);

            assertThat(successResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    // ========================================================================
    // Health Check Test
    // ========================================================================

    @Test
    @DisplayName("Health endpoint returns 200")
    void healthCheckReturns200() {
        ResponseEntity<String> response = restTemplate.getForEntity("/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
