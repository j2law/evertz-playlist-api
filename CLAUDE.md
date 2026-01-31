# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an HTTP REST API service for managing broadcaster channel playlists with strict ordering guarantees and optimistic concurrency control. The service handles playlists of 1,000-4,000 items across 100+ channels, with paginated access and multi-user editing scenarios.

**Primary Goal:** Correctness and testability over performance. E2E tests through HTTP endpoints are the main deliverable.

## Critical Architectural Concepts

### 1. Optimistic Concurrency Control (Fingerprint Pattern)

The fingerprint system prevents stale client state from corrupting playlist order:

- **Server computes**: `serverFingerprint` - deterministic hash representing current playlist state
- **Client sends**: `clientFingerprint` - their last-known fingerprint on ALL mutation operations (insert, delete, move)
- **Validation**: Server compares fingerprints BEFORE applying any change
- **Conflict response**: HTTP 409 with `{ "errorCode": "PLAYLIST_FINGERPRINT_MISMATCH", "serverFingerprint": "..." }`

**Key insight**: Every insert/delete/move MUST validate fingerprint first. This prevents race conditions where Client A and Client B both try to "insert at index 0" based on stale state.

**Design decisions to document in DESIGN.md:**
- What data goes into fingerprint? (itemId only? itemId+index? full item content?)
- Hash algorithm choice (MD5, SHA256, content-based hash?)
- Collision risk vs computation cost tradeoff

### 2. Ordered List Invariants (The "Healthy Playlist" Rules)

The playlist MUST maintain these invariants after EVERY operation:

1. **Contiguous indexing**: No gaps (0,1,2,3... OR 1,2,3,4...)
2. **No duplicates**: Each index appears exactly once
3. **Complete coverage**: N items means indexes span 0..N-1 (or 1..N)
4. **Deterministic ordering**: Listing by index returns all items exactly once

**Operations that maintain invariants:**

- **INSERT at index I**: Shift items [I..end] by +1, then place new item at I
- **DELETE at index I**: Remove item, shift items [I+1..end] by -1
- **MOVE from I to J**:
  - If I < J: Shift items [I+1..J] by -1, place item at J
  - If I > J: Shift items [J..I-1] by +1, place item at J
  - If I == J: No-op (test this edge case)

**Edge cases to handle (document in ASSUMPTIONS.md):**
- Insert at index > N (append? error? clamp?)
- Move to index > N (clamp? error?)
- Delete non-existent itemId (404? 204?)
- Operations on empty playlist
- Operations on non-existent channel

### 3. Pagination Under Mutation

**Challenge**: Client fetches page 1, then page 2, but between requests someone inserts/deletes items. What happens?

**Decisions to make:**
- **Cursor pagination**: Encode position in opaque cursor (more complex, handles mutations better)
- **Offset pagination**: Simple offset/limit (simpler, but can skip/duplicate items if list changes)

**Document in DESIGN.md**: Which approach you chose and why. If offset-based, acknowledge the "phantom read" risk and explain it's acceptable for this use case.

**Test requirement**: Pagination test MUST verify all items returned exactly once across pages.

## Common Commands

### Development Workflow
```bash
# Install dependencies
npm ci                          # Node.js
pip install -r requirements.txt # Python

# Run service
npm start                       # Typically starts on localhost:3000 or 8080
python -m uvicorn main:app      # Python example

# Run tests
npm test                        # E2E tests should start server automatically
pytest                          # Python

# Manual testing
curl http://localhost:3000/health
```

### Sample API Workflow
```bash
# 1. Get initial state
curl http://localhost:3000/api/channels/CH1/playlist/items?limit=50

# 2. Extract serverFingerprint from response, use as clientFingerprint

# 3. Insert item at index 0
curl -X POST http://localhost:3000/api/channels/CH1/playlist/items \
  -H "Content-Type: application/json" \
  -d '{"title":"Breaking News","index":0,"clientFingerprint":"abc123..."}'

# 4. Attempt with stale fingerprint (should get 409)
curl -X POST http://localhost:3000/api/channels/CH1/playlist/items \
  -H "Content-Type: application/json" \
  -d '{"title":"Another Item","index":0,"clientFingerprint":"OLD_FINGERPRINT"}'

# 5. Delete item
curl -X DELETE http://localhost:3000/api/channels/CH1/playlist/items/ITEM_ID \
  -H "Content-Type: application/json" \
  -d '{"clientFingerprint":"current_fingerprint"}'

# 6. Move item
curl -X POST http://localhost:3000/api/channels/CH1/playlist/items/ITEM_ID/move \
  -H "Content-Type: application/json" \
  -d '{"newIndex":5,"clientFingerprint":"current_fingerprint"}'

# 7. Sync check
curl -X POST http://localhost:3000/api/channels/CH1/playlist/sync-check \
  -H "Content-Type: application/json" \
  -d '{"clientFingerprint":"current_fingerprint"}'
```

## Implementation Strategy

### Critical Implementation Points

**1. Atomicity of Shift Operations**
- Fingerprint check + shift + update MUST be atomic (single transaction/lock)
- If using in-memory: Ensure proper locking
- If using database: Use transactions or atomic updates

**2. Fingerprint Computation**
- Must be deterministic (same playlist → same fingerprint)
- Should change on ANY mutation (even if order unchanged? Document this)
- Consider: Hash of sorted itemIds? Hash of (index, itemId) pairs?

**3. Index Shifting Logic**
```
INSERT at targetIndex:
  for each item where index >= targetIndex:
    item.index += 1
  newItem.index = targetIndex

DELETE item at currentIndex:
  for each item where index > currentIndex:
    item.index -= 1

MOVE from oldIndex to newIndex:
  if oldIndex < newIndex:
    for each item where oldIndex < index <= newIndex:
      item.index -= 1
  else if oldIndex > newIndex:
    for each item where newIndex <= index < oldIndex:
      item.index += 1
  item.index = newIndex
```

**4. Pagination Consistency**
- Always order by index ASC
- Include `serverFingerprint` in EVERY list response
- If cursor-based: Encode enough info to resume (e.g., last index, last itemId)

## Testing Strategy

### E2E Test Categories

1. **Ordering invariants** - After each operation, verify contiguous, no gaps, no duplicates
2. **Insert scenarios** - start, middle, end, out-of-range
3. **Delete scenarios** - first, middle, last, non-existent
4. **Move scenarios** - forward, backward, same index, out-of-range
5. **Pagination** - multi-page iteration, completeness, determinism
6. **Fingerprint mismatch** - successful mutation, 409 on stale client

### Test Patterns

**Helper function** to verify playlist health:
```javascript
async function assertPlaylistHealthy(channelId) {
  const response = await fetch(`/api/channels/${channelId}/playlist/items?limit=9999`);
  const { items, totalCount } = await response.json();

  // Assert count matches
  assert.equal(items.length, totalCount);

  // Assert contiguous (0-based example)
  items.forEach((item, idx) => {
    assert.equal(item.index, idx);
  });

  // Assert no duplicate indexes
  const indexes = items.map(i => i.index);
  assert.equal(new Set(indexes).size, indexes.length);
}
```

**Fingerprint mismatch pattern**:
```javascript
// Get current fingerprint
const list1 = await fetch('/api/channels/CH1/playlist/items').then(r => r.json());
const fp1 = list1.serverFingerprint;

// Make a change
await fetch('/api/channels/CH1/playlist/items', {
  method: 'POST',
  body: JSON.stringify({ title: 'Item', index: 0, clientFingerprint: fp1 })
});

// Try to use old fingerprint (should fail with 409)
const response = await fetch('/api/channels/CH1/playlist/items', {
  method: 'POST',
  body: JSON.stringify({ title: 'Item2', index: 0, clientFingerprint: fp1 })
});
assert.equal(response.status, 409);
```

## API Endpoints

- **GET /health** - Health check
- **GET /api/channels/{channelId}/playlist/items** - List items (paginated, ordered by index)
- **POST /api/channels/{channelId}/playlist/items** - Insert item at index
- **DELETE /api/channels/{channelId}/playlist/items/{itemId}** - Delete item
- **POST /api/channels/{channelId}/playlist/items/{itemId}/move** - Move item to new index
- **POST /api/channels/{channelId}/playlist/sync-check** - Verify client fingerprint matches server

## Key Specification Details

- **Channels**: 100+ channels, each with ONE playlist
- **Playlist size**: 1,000-4,000 items (design for this scale)
- **itemId generation**: Your choice (server-generated UUID vs client-provided) - document it
- **Indexing base**: Your choice (0-based vs 1-based) - MUST document in ASSUMPTIONS.md
- **Persistence**: Required, choice is yours (SQLite, JSON file, PostgreSQL, in-memory with WAL)
- **Technology stack**: Open (Node.js, Python, Go, etc.)

## Documentation Deliverables

1. **README.md** - Exact run steps, test steps, base URL, sample curl commands
2. **DESIGN.md** - Persistence, pagination, fingerprint algorithm, shifting implementation, API schema pattern
3. **ASSUMPTIONS.md** - Indexing scheme, out-of-range behavior, error handling, edge cases
4. **prompts.md** - (Optional) AI interaction log if using AI assistance

## Potential Pitfalls

1. **Forgetting to shift indexes** - Every insert/delete/move must update affected items
2. **Non-atomic operations** - Race condition between fingerprint check and mutation
3. **Pagination bugs** - Forgetting to order by index, wrong offset calculation
4. **Fingerprint not deterministic** - Using timestamps or random data in hash
5. **Missing 409 responses** - Forgetting to validate clientFingerprint on delete/move
6. **Out-of-range handling** - Not documenting/testing insert at index=9999 on 10-item list

## Implementation Checklist

Design decisions that MUST be documented in ASSUMPTIONS.md before coding:

- [ ] 0-based or 1-based indexing?
- [ ] Server-generated or client-provided itemIds?
- [ ] Cursor or offset pagination?
- [ ] What goes into fingerprint computation?
- [ ] How to handle insert at index > N? (append, error, clamp to N)
- [ ] How to handle move to index > N?
- [ ] What HTTP status for delete non-existent item? (404, 204, 400)
- [ ] Where does clientFingerprint go on DELETE? (request body, header, query param)
- [ ] What persistence layer? (SQLite, PostgreSQL, file, memory+WAL)
- [ ] How to ensure shift operations are atomic?
