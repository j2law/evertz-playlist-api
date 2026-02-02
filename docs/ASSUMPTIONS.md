# ASSUMPTIONS.md

This document outlines the design decisions and assumptions made for the Playlist API.

---

## 1. Indexing Scheme

**Decision:** 0-based indexing

- First item is at index `0`
- Valid insert range: `0` to `N` (where N = current item count; inserting at N appends)
- Valid move range: `0` to `N-1` (existing positions only)
- Aligns with Java arrays/lists and standard API conventions

---

## 2. Item ID Generation

**Decision:** Server-generated UUID

- Server generates `UUID.randomUUID()` on insert
- Client does not provide itemId
- Guarantees uniqueness without client coordination
- Returned in response so client can reference it for delete/move

---

## 3. Pagination

**Decision:** Offset-based pagination

**Parameters:**
- `offset` (default: 0) - number of items to skip
- `limit` (default: 50, max: 100) - number of items to return

**Behavior under concurrent edits:**
- Offset pagination can result in duplicates or skipped items if the list changes between page requests
- Every response includes `serverFingerprint`
- Client can detect staleness by comparing fingerprints across pages
- Client decides whether to continue or restart pagination

**Rationale:** Simpler implementation; fingerprint provides staleness detection; cursor pagination still struggles with move operations.

---

## 4. Fingerprint Computation

**Decision:** SHA-256 hash of ordered `index:itemId` pairs

**Algorithm:**
1. Fetch all items ordered by index ASC
2. Build string: `"0:itemId0|1:itemId1|2:itemId2|..."`
3. Compute SHA-256 hash
4. Return as hex string (lowercase)

**Example:**
```
Items: [{index:0, id:"abc"}, {index:1, id:"def"}, {index:2, id:"ghi"}]
String: "0:abc|1:def|2:ghi"
Fingerprint: SHA256("0:abc|1:def|2:ghi") → "a1b2c3d4..."
```

**Properties:**
- Deterministic (same state → same fingerprint)
- Changes on any insert, delete, or move
- Does NOT include item content (title changes don't affect fingerprint)

---

## 5. Out-of-Range Index Handling

**Decision:** Strict validation with 400 Bad Request

**Insert:**
- Valid range: `0` to `N` (inserting at N appends)
- Index < 0: 400 Bad Request
- Index > N: 400 Bad Request

**Move:**
- Valid range: `0` to `N-1` (existing positions only)
- Index < 0: 400 Bad Request
- Index >= N: 400 Bad Request

**Error response:**
```json
{
  "errorCode": "INVALID_INDEX",
  "message": "Index 50 is out of range. Valid range is 0 to 10"
}
```

**Rationale:** Clients must provide valid fingerprint to mutate, meaning they've fetched recent state and know the list size. Out-of-range index indicates a client bug that should be surfaced.

---

## 6. Delete Non-Existent Item

**Decision:** 404 Not Found

**Response:**
```json
{
  "errorCode": "NOT_FOUND",
  "message": "Item not found: xyz"
}
```

**Rationale:** Explicit feedback helps debugging. Client knows the item wasn't there.

---

## 7. Client Fingerprint Location

**Decision:** Request body for all mutation operations

**Insert:** `POST /api/channels/CH1/playlist/items`
```json
{ "title": "...", "index": 0, "clientFingerprint": "abc123" }
```

**Delete:** `DELETE /api/channels/CH1/playlist/items/ITEM_ID`
```json
{ "clientFingerprint": "abc123" }
```

**Move:** `POST /api/channels/CH1/playlist/items/ITEM_ID/move`
```json
{ "newIndex": 5, "clientFingerprint": "abc123" }
```

**Rationale:** Consistent across all mutation endpoints. Spring handles DELETE with request body.

---

## 8. Atomicity of Shift Operations

**Decision:** Single service method with JPA batch updates

- Fingerprint validation + index shifts + item insert/delete/move happen in one service method
- JPA flushes all changes at method boundary
- Use `@Modifying` queries for bulk index updates where appropriate
- Database unique constraint on `(channel_id, index)` prevents invalid states

---

## 9. Channel Behavior

**Decision:** Auto-create channels on first use

- No explicit channel creation endpoint needed
- First insert to a channel creates it implicitly
- GET on non-existent channel returns empty playlist with valid fingerprint
- Simplifies API surface

---

## 10. Empty Playlist Fingerprint

**Decision:** Hash of empty string

- Empty playlist has a valid, consistent fingerprint
- Clients can insert into empty playlist by providing this fingerprint
- `SHA256("")` → `"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"`

---

## 11. Malformed Request Handling

**Decision:** 400 Bad Request for validation errors

### Custom Validation (with custom error format)

| Validation | Error Code | Example |
|------------|------------|---------|
| Missing clientFingerprint | `VALIDATION_ERROR` | No `clientFingerprint` on mutation |
| Empty/blank title | `VALIDATION_ERROR` | `title: ""` or `title: "   "` |
| Negative index | `INVALID_INDEX` | `index: -1` |
| Out-of-range index | `INVALID_INDEX` | `index: 9999` on small list |
| Invalid pagination | `INVALID_PAGINATION` | `offset: -1` or `limit: 0` or `limit: 500` |

**Example responses:**

Missing clientFingerprint:
```json
{
  "errorCode": "VALIDATION_ERROR",
  "message": "clientFingerprint is required"
}
```

Empty/blank title:
```json
{
  "errorCode": "VALIDATION_ERROR",
  "message": "Title must not be empty or blank"
}
```

Invalid index:
```json
{
  "errorCode": "INVALID_INDEX",
  "message": "Index must be non-negative"
}
```

Invalid pagination:
```json
{
  "errorCode": "INVALID_PAGINATION",
  "message": "Limit must be between 1 and 100"
}
```

### Spring Default Handling (with Spring's error format)

| Validation | HTTP Status | Example |
|------------|-------------|---------|
| Invalid field type | 400 | `index: "abc"` instead of number |
| Malformed JSON | 400 | Unparseable JSON |
| Missing request body | 400 | DELETE with no body |

These cases return Spring's default error format:
```json
{
  "timestamp": "2026-02-01T...",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/channels/CH1/playlist/items"
}
```

**Pagination constraints:**
- `offset`: must be >= 0
- `limit`: must be >= 1 and <= 100

**Title constraints:**
- Must not be null, empty, or blank (validated via `@NotBlank`)

**clientFingerprint constraints:**
- Must not be null (validated via `@NotNull`)

---

## HTTP Status Code Summary

| Status | When |
|--------|------|
| 200 | Success (GET, DELETE, move, sync-check) |
| 201 | Success (insert - resource created) |
| 400 | Malformed request, validation errors |
| 404 | Item not found |
| 409 | Fingerprint mismatch |

---

## Fingerprint Mismatch Response (409)

```json
{
  "errorCode": "PLAYLIST_FINGERPRINT_MISMATCH",
  "serverFingerprint": "current_fingerprint_value"
}
```
