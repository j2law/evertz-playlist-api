# Design Document

## Overview

This document describes the key design decisions for the Broadcaster Playlist Service API.

## Client Fingerprint Transmission

### Decision: Request Body

The `clientFingerprint` is passed via **request body** for all mutation operations (insert, delete, move, sync-check).

### Alternatives Considered

| Method | Pros | Cons |
|--------|------|------|
| **Request Body** | Consistent with REST semantics for POST/DELETE with payloads; easy to document in OpenAPI; keeps all request data together | DELETE with body is less common (but valid per HTTP spec) |
| Header (`X-Client-Fingerprint`) | Works for all HTTP methods; doesn't require body for DELETE | Non-standard header; harder to discover; separates related data |
| Query Parameter | Simple; works for all methods | Exposes fingerprint in URL logs; limited length; not semantic |

### Rationale

1. **Consistency**: All mutation endpoints use the same pattern - fingerprint in the request body alongside other operation data
2. **Semantics**: The fingerprint is logically part of the operation request, not metadata
3. **Discoverability**: Request body fields are clearly documented in OpenAPI/Swagger
4. **Security**: Fingerprints don't appear in URL logs or browser history

### Implementation

**Insert** (`POST /api/channels/{channelId}/playlist/items`):
```json
{ "title": "...", "index": 0, "clientFingerprint": "..." }
```

**Delete** (`DELETE /api/channels/{channelId}/playlist/items/{itemId}`):
```json
{ "clientFingerprint": "..." }
```

**Move** (`POST /api/channels/{channelId}/playlist/items/{itemId}/move`):
```json
{ "newIndex": 5, "clientFingerprint": "..." }
```

**Sync-check** (`POST /api/channels/{channelId}/playlist/sync-check`):
```json
{ "clientFingerprint": "..." }
```

## Fingerprint Algorithm

### Decision: SHA-256 of Ordered Index:ItemId Pairs

The server fingerprint is computed as:
```
SHA-256("0:itemId0|1:itemId1|2:itemId2|...")
```

### Rationale

1. **Deterministic**: Same playlist state always produces the same fingerprint
2. **Order-sensitive**: Captures both item identity and position
3. **Collision-resistant**: SHA-256 provides sufficient uniqueness for this use case
4. **Efficient**: Linear scan of playlist items; hash computation is fast

### Empty Playlist

An empty playlist produces the SHA-256 hash of an empty string:
```
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
```

## Pagination Strategy

### Decision: Offset-Based Pagination

Using simple offset/limit pagination with fingerprint staleness detection.

### Response Structure

```json
{
  "items": [],
  "page": {
    "limit": 50,
    "offset": 0,
    "nextOffset": 50,
    "hasMore": true
  },
  "totalCount": 100,
  "serverFingerprint": "abc123..."
}
```

### Tradeoffs

| Aspect | Offset Pagination | Cursor Pagination |
|--------|------------------|-------------------|
| Simplicity | Simple to implement and use | More complex |
| Mutation handling | Can skip/duplicate items if list changes | Handles mutations better |
| Random access | Supports jumping to any page | Sequential only |

### Mitigation

The `serverFingerprint` in every response allows clients to detect when the playlist has changed between page fetches. Clients can re-fetch from the beginning if needed.

## Persistence Layer

### Decision: H2 Embedded Database with Spring Data JPA

- **H2**: Embedded, file-based database for persistence across restarts
- **Spring Data JPA**: Standard ORM with declarative queries

### Why H2?

| Reason | Explanation |
|--------|-------------|
| **Zero configuration** | No external database server required; runs embedded in the JVM |
| **File-based persistence** | Data survives process restarts without external dependencies |
| **SQL compatibility** | Supports standard SQL, making it easy to migrate to PostgreSQL/MySQL later |
| **Spring Boot integration** | Auto-configured with Spring Boot; minimal setup required |
| **Fast for testing** | In-memory mode available for fast test execution |

### Why Spring Data JPA?

| Reason | Explanation |
|--------|-------------|
| **Declarative repositories** | Query methods derived from method names; less boilerplate |
| **Transaction management** | `@Transactional` handles commit/rollback automatically |
| **Portability** | Can switch databases without changing repository code |
| **Bulk operations** | `@Query` and `@Modifying` support efficient batch updates |

### Limitations

| Limitation | Impact | Mitigation |
|------------|--------|------------|
| **Single-process only** | H2 file mode doesn't support multiple JVM connections | Acceptable for this use case; use PostgreSQL for multi-instance deployment |
| **Not production-grade** | H2 is designed for development/testing, not high-load production | Replace with PostgreSQL/MySQL for production; schema is compatible |
| **No horizontal scaling** | Embedded database can't be shared across instances | Would need external database + connection pooling for scaling |
| **Limited concurrency** | File-based H2 has lower concurrent write throughput | Sufficient for 100+ channels with moderate write load |

### Data Model

```sql
CREATE TABLE playlist_items (
    id VARCHAR(36) PRIMARY KEY,
    channel_id VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    item_index INT NOT NULL,
    UNIQUE (channel_id, item_index)
);

CREATE INDEX idx_channel_index ON playlist_items(channel_id, item_index);
CREATE INDEX idx_channel_id ON playlist_items(channel_id);
```

The unique constraint on `(channel_id, item_index)` ensures playlist integrity by preventing duplicate indexes within a channel.

## Index Shifting Implementation

### How Shifting is Implemented Safely

The shifting implementation uses multiple layers of safety:

**1. Transactional Atomicity**

All mutation operations (insert, delete, move) are wrapped in Spring's `@Transactional` annotation. This ensures:
- All database operations within a mutation succeed or fail together
- On any failure (constraint violation, exception), all changes are rolled back
- The playlist is never left in an inconsistent state

**2. Fingerprint-First Validation**

Every mutation validates the fingerprint BEFORE making any changes:
```
1. Compute current server fingerprint
2. Compare with client fingerprint
3. If mismatch → throw exception (no changes made)
4. If match → proceed with mutation
```

This prevents stale clients from corrupting the playlist state.

**3. Bulk Update Queries**

Index shifting uses bulk UPDATE queries rather than loading and saving individual entities:
```sql
UPDATE playlist_items SET item_index = item_index + 1
WHERE channel_id = ? AND item_index >= ?
```

This is:
- **Atomic**: Single SQL statement
- **Efficient**: No N+1 query problem
- **Safe**: Database handles concurrent access

**4. Operation Ordering**

Each operation follows a specific order to maintain invariants:

- **Insert**: Shift existing items first, then insert new item
- **Delete**: Delete item first, then shift remaining items
- **Move**: Use temporary index, shift affected range, set final index

### Unique Constraint Handling

The database has a unique constraint on `(channel_id, item_index)` to enforce playlist integrity. This constraint can cause violations during shift operations if not handled carefully.

**Solution for Move Operations**: Use a temporary index (-1) to avoid collisions:
1. Move the item to temporary index (-1)
2. Shift affected items
3. Move the item to its final index

This three-step approach ensures no two items temporarily share the same index during the operation.

### Shifting Logic

```
INSERT at targetIndex:
  UPDATE SET index = index + 1 WHERE channelId = ? AND index >= targetIndex
  INSERT new item at targetIndex

DELETE at deletedIndex:
  DELETE item
  UPDATE SET index = index - 1 WHERE channelId = ? AND index > deletedIndex

MOVE from oldIndex to newIndex:
  UPDATE item SET index = -1  (temporary index to avoid constraint violation)
  If oldIndex < newIndex:
    UPDATE SET index = index - 1 WHERE channelId = ? AND index > oldIndex AND index <= newIndex
  Else if oldIndex > newIndex:
    UPDATE SET index = index + 1 WHERE channelId = ? AND index >= newIndex AND index < oldIndex
  UPDATE item SET index = newIndex
```

## API Endpoints Summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Health check |
| GET | `/api/channels/{channelId}/playlist/items` | List items (paginated) |
| POST | `/api/channels/{channelId}/playlist/items` | Insert item at index |
| DELETE | `/api/channels/{channelId}/playlist/items/{itemId}` | Delete item |
| POST | `/api/channels/{channelId}/playlist/items/{itemId}/move` | Move item to new index |
| POST | `/api/channels/{channelId}/playlist/sync-check` | Verify fingerprint match |

### Success Responses

| Endpoint | Status | Response Body |
|----------|--------|---------------|
| List items | 200 | `{ items, page, totalCount, serverFingerprint }` |
| Insert | 201 | `{ item, serverFingerprint }` |
| Delete | 200 | `{ serverFingerprint }` |
| Move | 200 | `{ item, serverFingerprint }` |
| Sync-check | 200 | `{ serverFingerprint }` |

## API Schema Pattern

### Decision: REST with Structured Error Responses

Following REST conventions with consistent error response format:

**Standard errors** (400, 404):
```json
{ "errorCode": "ERROR_CODE", "message": "Human-readable message" }
```

**Fingerprint mismatch** (409):
```json
{ "errorCode": "PLAYLIST_FINGERPRINT_MISMATCH", "serverFingerprint": "..." }
```

### Error Codes

| HTTP Status | Error Code | Description |
|-------------|------------|-------------|
| 400 | `INVALID_PAGINATION` | Invalid offset/limit parameters |
| 400 | `INVALID_INDEX` | Index out of valid range |
| 404 | `NOT_FOUND` | Item or resource not found |
| 409 | `PLAYLIST_FINGERPRINT_MISMATCH` | Client fingerprint doesn't match server |

## Assumptions & Limitations

### Key Assumptions

- **0-based indexing**: Indexes run from `0` to `N-1`
- **Server-generated IDs**: Server generates UUIDs; clients don't provide itemId
- **Auto-created channels**: Channels are created implicitly on first insert
- **Fingerprint scope**: Only item IDs and positions affect fingerprint (not title changes)

### Known Limitations

- **Single-process only**: H2 file mode doesn't support multiple JVM connections
- **Not production-grade**: H2 is designed for development/testing; use PostgreSQL/MySQL for production
- **Offset pagination under mutation**: Can skip/duplicate items if list changes between page fetches (mitigated by fingerprint detection)
- **No authentication**: API has no auth layer; would need to add for production

For detailed documentation of all assumptions and edge case handling, see [ASSUMPTIONS.md](ASSUMPTIONS.md).
