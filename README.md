# Broadcaster Playlist Service API

An HTTP REST API service for managing broadcaster channel playlists with strict ordering guarantees and optimistic concurrency control.

## Prerequisites

- Java 21 or higher
- Maven 3.8+ (or use the included Maven wrapper)

## Run Steps

### 1. Clone and navigate to the project

```bash
cd evertz_assignment_1
```

### 2. Build the project

```bash
./mvnw clean install -DskipTests
```

### 3. Run the service

```bash
./mvnw spring-boot:run
```

The service will start on **http://localhost:8080**

### 4. Verify the service is running

```bash
curl http://localhost:8080/health
```

Expected response: `OK`

## Test Steps

### Run all tests

```bash
./mvnw test
```

### Run only E2E tests

```bash
./mvnw test -Dtest="PlaylistEndToEndTest"
```

## Base URL and Port

- **Base URL**: `http://localhost:8080`
- **Port**: `8080`
- **API Base Path**: `/api/channels/{channelId}/playlist`

## API Documentation

Swagger UI is available at: http://localhost:8080/swagger-ui.html

## Sample curl Commands

### 1. Health Check

```bash
curl http://localhost:8080/health
```

### 2. List Playlist Items (Paginated)

```bash
# Get first page (default limit=50)
curl http://localhost:8080/api/channels/CH1/playlist/items

# With pagination parameters
curl "http://localhost:8080/api/channels/CH1/playlist/items?offset=0&limit=10"
```

**Response:**
```json
{
  "items": [],
  "page": {
    "limit": 50,
    "offset": 0,
    "nextOffset": null,
    "hasMore": false
  },
  "totalCount": 0,
  "serverFingerprint": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
}
```

### 3. Insert Item at Index

```bash
# First, get the current fingerprint
curl http://localhost:8080/api/channels/CH1/playlist/items

# Insert item at index 0 (use serverFingerprint from previous response)
curl -X POST http://localhost:8080/api/channels/CH1/playlist/items \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Breaking News",
    "index": 0,
    "clientFingerprint": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
  }'
```

**Response (201 Created):**
```json
{
  "item": {
    "itemId": "550e8400-e29b-41d4-a716-446655440000",
    "index": 0,
    "title": "Breaking News"
  },
  "serverFingerprint": "a1b2c3d4..."
}
```

### 4. Delete Item

```bash
# Use the itemId from a previous response and current serverFingerprint
curl -X DELETE http://localhost:8080/api/channels/CH1/playlist/items/ITEM_ID \
  -H "Content-Type: application/json" \
  -d '{
    "clientFingerprint": "current_fingerprint_here"
  }'
```

**Response (200 OK):**
```json
{
  "serverFingerprint": "new_fingerprint..."
}
```

### 5. Move Item to New Index

```bash
curl -X POST http://localhost:8080/api/channels/CH1/playlist/items/ITEM_ID/move \
  -H "Content-Type: application/json" \
  -d '{
    "newIndex": 5,
    "clientFingerprint": "current_fingerprint_here"
  }'
```

**Response (200 OK):**
```json
{
  "item": {
    "itemId": "550e8400-e29b-41d4-a716-446655440000",
    "index": 5,
    "title": "Breaking News"
  },
  "serverFingerprint": "new_fingerprint..."
}
```

### 6. Sync Check (Verify Fingerprint)

```bash
curl -X POST http://localhost:8080/api/channels/CH1/playlist/sync-check \
  -H "Content-Type: application/json" \
  -d '{
    "clientFingerprint": "current_fingerprint_here"
  }'
```

**Response (200 OK if match):**
```json
{
  "serverFingerprint": "current_fingerprint_here"
}
```

**Response (409 Conflict if mismatch):**
```json
{
  "errorCode": "PLAYLIST_FINGERPRINT_MISMATCH",
  "serverFingerprint": "actual_server_fingerprint"
}
```

## Complete Workflow Example

```bash
# 1. Get initial state and fingerprint for an empty channel
RESPONSE=$(curl -s http://localhost:8080/api/channels/CH1/playlist/items)
echo $RESPONSE
FP=$(echo $RESPONSE | grep -o '"serverFingerprint":"[^"]*"' | cut -d'"' -f4)

# 2. Insert first item
RESPONSE=$(curl -s -X POST http://localhost:8080/api/channels/CH1/playlist/items \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"Item 1\",\"index\":0,\"clientFingerprint\":\"$FP\"}")
echo $RESPONSE
FP=$(echo $RESPONSE | grep -o '"serverFingerprint":"[^"]*"' | cut -d'"' -f4)
ITEM_ID=$(echo $RESPONSE | grep -o '"itemId":"[^"]*"' | cut -d'"' -f4)

# 3. Insert second item
RESPONSE=$(curl -s -X POST http://localhost:8080/api/channels/CH1/playlist/items \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"Item 2\",\"index\":1,\"clientFingerprint\":\"$FP\"}")
echo $RESPONSE
FP=$(echo $RESPONSE | grep -o '"serverFingerprint":"[^"]*"' | cut -d'"' -f4)

# 4. List all items
curl -s http://localhost:8080/api/channels/CH1/playlist/items

# 5. Move first item to end
RESPONSE=$(curl -s -X POST "http://localhost:8080/api/channels/CH1/playlist/items/$ITEM_ID/move" \
  -H "Content-Type: application/json" \
  -d "{\"newIndex\":1,\"clientFingerprint\":\"$FP\"}")
echo $RESPONSE

# 6. List items again to see the change
curl -s http://localhost:8080/api/channels/CH1/playlist/items
```

## Error Responses

| HTTP Status | Error Code | Description |
|-------------|------------|-------------|
| 400 | `VALIDATION_ERROR` | Missing required field or invalid value |
| 400 | `INVALID_INDEX` | Index out of valid range |
| 400 | `INVALID_PAGINATION` | Invalid offset/limit parameters |
| 404 | `NOT_FOUND` | Item not found |
| 409 | `PLAYLIST_FINGERPRINT_MISMATCH` | Client fingerprint doesn't match server |

## Project Structure

```
src/
├── main/java/com/evertz/playlist/
│   ├── core/                    # Domain layer (no framework dependencies)
│   │   ├── model/               # Domain entities
│   │   └── exception/           # Domain exceptions
│   ├── application/             # Application layer
│   │   ├── service/             # Business logic
│   │   └── port/                # Persistence abstractions
│   └── infrastructure/          # Infrastructure layer
│       ├── api/                 # REST controllers and DTOs
│       └── persistence/         # JPA entities and repositories
└── test/java/com/evertz/playlist/
    └── integration/             # E2E tests
```

## Documentation

- [DESIGN.md](docs/DESIGN.md) - Design decisions and tradeoffs
- [ASSUMPTIONS.md](docs/ASSUMPTIONS.md) - Assumptions and edge case handling
