# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an HTTP REST API service for managing broadcaster channel playlists with strict ordering guarantees and optimistic concurrency control. The service handles playlists of 1,000-4,000 items across 100+ channels, with paginated access and multi-user editing scenarios.

**Primary Goal:** Correctness and testability over performance. E2E tests through HTTP endpoints are the main deliverable.

## Technology Stack

- **Framework**: Spring Boot 3.x
- **Language**: Java 21 (use modern features: records, pattern matching, sealed classes where appropriate)
- **Database**: H2 (embedded, file-based for persistence)
- **ORM**: Spring Data JPA
- **API Documentation**: SpringDoc OpenAPI (Swagger UI)
- **Testing**: JUnit 5, Mockito, @SpringBootTest, @WebMvcTest

## Architectural Principles

### SOLID Principles (MUST follow)

1. **Single Responsibility**: Each class has one reason to change
   - Controllers handle HTTP concerns only
   - Services contain business logic only
   - Repositories handle data access only

2. **Open/Closed**: Open for extension, closed for modification
   - Use interfaces for services and repositories
   - Favor composition over inheritance

3. **Liskov Substitution**: Subtypes must be substitutable for their base types
   - Interface implementations must honor contracts

4. **Interface Segregation**: No client should depend on methods it doesn't use
   - Keep interfaces focused and cohesive

5. **Dependency Inversion**: Depend on abstractions, not concretions
   - Use constructor injection
   - Program to interfaces

### Clean Architecture (Layered Boundaries)

```
┌─────────────────────────────────────────────────────────────┐
│  Infrastructure Layer (Controllers, JPA Entities, Repos)    │
├─────────────────────────────────────────────────────────────┤
│  Application Layer (Services, Persistence Interfaces/Ports) │
├─────────────────────────────────────────────────────────────┤
│  Core Layer (Domain Entities, Value Objects, Exceptions)    │
└─────────────────────────────────────────────────────────────┘
```

**Three-Layer Separation:**

1. **Core Layer** (`core/`) - Innermost, NO framework dependencies
   - Pure domain entities (plain Java classes)
   - Value objects
   - Domain exceptions
   - Business rules embedded in entities

2. **Application Layer** (`application/`) - Business logic orchestration
   - Service classes (concrete implementations, no interfaces per YAGNI)
   - Persistence port interfaces (abstractions for data access)
   - Use case orchestration
   - Depends only on Core layer

3. **Infrastructure Layer** (`infrastructure/`) - Framework-dependent implementations
   - REST Controllers + Request/Response DTOs
   - JPA DAOs (separate from Core entities, with `@Entity` annotations)
   - Spring Data JPA Repositories (implements persistence ports)
   - Adapters that map between JPA DAOs and Core entities
   - Configuration classes
   - Depends on Application and Core layers

**Package Structure:**
```
com.evertz.playlist
├── core/                        # NO framework dependencies
│   ├── model/                   # Pure domain entities
│   └── exception/               # Domain exceptions
├── application/                 # Business logic layer
│   ├── service/                 # Service classes (concrete, no interfaces per YAGNI)
│   └── port/                    # Persistence port interfaces
└── infrastructure/              # Framework-dependent
    ├── api/                     # REST controllers
    │   ├── controller/          # Controllers + GlobalExceptionHandler
    │   └── dto/                 # Request/Response DTOs
    ├── persistence/             # Database layer
    │   ├── dao/                 # JPA DAOs (with @Entity annotations)
    │   ├── repository/          # Spring Data JPA repositories
    │   └── adapter/             # Adapters implementing ports
    └── config/                  # Spring configuration
```

**Dependency Rules:**
- Infrastructure depends on Application and Core
- Application depends only on Core
- Core has ZERO external dependencies (no Spring, no JPA, no frameworks)
- Dependencies always point inward (toward Core)

**Key Principle:** Core entities are pure Java classes. JPA DAOs in Infrastructure are separate classes that map to/from Core entities. This keeps the domain model clean and testable without framework coupling.

### DTOs and Persistence

**DTOs (Data Transfer Objects):**
- Use Java Records for immutability
- Separate Request DTOs and Response DTOs
- DTOs live in `infrastructure/api/dto/`
- Manual mapping between DTOs and Core entities (no MapStruct/Lombok)

**Example:**
```java
// Request DTO (infrastructure/api/dto/)
public record CreatePlaylistItemRequest(
    String title,
    int index,
    String clientFingerprint
) {}

// Response DTOs (infrastructure/api/dto/)
public record PlaylistItemResponse(
    String itemId,
    int index,
    String title
) {}

public record PageInfo(
    int limit,
    int offset,
    Integer nextOffset,
    boolean hasMore
) {}

public record PlaylistResponse(
    List<PlaylistItemResponse> items,
    PageInfo page,
    int totalCount,
    String serverFingerprint
) {}

// Manual mapping in controller
PlaylistItemResponse itemResponse = new PlaylistItemResponse(
    coreEntity.getId(),
    coreEntity.getIndex(),
    coreEntity.getTitle()
);
```

**JPA DAOs vs Core Entities:**
- Core entities (`core/model/`) are pure Java classes with no annotations
- JPA DAOs (`infrastructure/persistence/dao/`) have `@Entity`, `@Table`, etc.
- Adapters map between JPA DAOs and Core entities

**Example:**
```java
// Core entity (core/model/) - NO framework dependencies
public class PlaylistItem {
    private final String id;
    private final String channelId;
    private final String title;
    private int index;
    // Pure Java, no annotations
}

// JPA DAO (infrastructure/persistence/dao/)
@Entity
@Table(name = "playlist_items")
public class PlaylistItemDao {
    @Id
    private String id;
    @Column(name = "channel_id")
    private String channelId;
    // JPA annotations for persistence
}
```

**Persistence Ports and Adapters:**
- Port interfaces live in `application/port/` (e.g., `PlaylistItemRepository`)
- Adapter implementations live in `infrastructure/persistence/adapter/`
- Spring Data JPA repositories are implementation details in infrastructure

### Constructor Injection (Required)

Always use constructor injection, never field injection:

```java
// Service in application layer depends on port interface (not JPA repository directly)
// No interface needed for services per YAGNI - use concrete class directly
@Service
public class PlaylistService {
    private final PlaylistItemPort playlistItemPort;  // Port interface, not JPA repo

    // Constructor injection - no @Autowired needed with single constructor
    public PlaylistService(PlaylistItemPort playlistItemPort) {
        this.playlistItemPort = playlistItemPort;
    }
}

// Adapter in infrastructure implements the port
@Component
public class PlaylistItemAdapter implements PlaylistItemPort {
    private final PlaylistItemJpaRepository jpaRepository;  // Spring Data JPA

    public PlaylistItemAdapter(PlaylistItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PlaylistItem findById(String id) {
        return jpaRepository.findById(id)
            .map(this::toCoreEntity)
            .orElse(null);
    }

    private PlaylistItem toCoreEntity(PlaylistItemDao dao) {
        return new PlaylistItem(dao.getId(), dao.getChannelId(),
                                dao.getTitle(), dao.getIndex());
    }
}
```

### Exception Handling

**Global Exception Handler with @ControllerAdvice:**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FingerprintMismatchException.class)
    public ResponseEntity<ErrorResponse> handleFingerprintMismatch(FingerprintMismatchException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("PLAYLIST_FINGERPRINT_MISMATCH", ex.getServerFingerprint()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }
}
```

**Custom Exceptions:**
- `FingerprintMismatchException` - for 409 conflicts
- `ResourceNotFoundException` - for 404 errors
- `InvalidOperationException` - for 400 bad requests

### OOP Principles

- **Encapsulation**: Keep fields private, expose behavior through methods
- **Abstraction**: Hide implementation details behind interfaces
- **Composition over Inheritance**: Favor has-a over is-a relationships
- **Immutability**: Use records for DTOs, make entities mutable only where necessary

### YAGNI (You Ain't Gonna Need It)

- **Don't create code until you need it** - No placeholder implementations, no "just in case" abstractions
- **Add features incrementally** - Start minimal, add as requirements emerge
- **Delete unused code** - If it's not being used, remove it
- **Avoid premature abstraction** - Don't create interfaces for single implementations until needed
- **Services don't need interfaces** - Application layer services are concrete classes; only create interfaces for persistence ports (where we have adapter pattern)

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
# Build the project
./mvnw clean install

# Run the service (starts on localhost:8080)
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run only unit tests
./mvnw test -Dtest="*Test"

# Run only integration tests
./mvnw test -Dtest="*IT"

# Manual testing
curl http://localhost:8080/health

# Access Swagger UI
# Open browser: http://localhost:8080/swagger-ui.html
```

### Sample API Workflow
```bash
# 1. Get initial state
curl http://localhost:8080/api/channels/CH1/playlist/items?limit=50

# 2. Extract serverFingerprint from response, use as clientFingerprint

# 3. Insert item at index 0
curl -X POST http://localhost:8080/api/channels/CH1/playlist/items \
  -H "Content-Type: application/json" \
  -d '{"title":"Breaking News","index":0,"clientFingerprint":"abc123..."}'

# 4. Attempt with stale fingerprint (should get 409)
curl -X POST http://localhost:8080/api/channels/CH1/playlist/items \
  -H "Content-Type: application/json" \
  -d '{"title":"Another Item","index":0,"clientFingerprint":"OLD_FINGERPRINT"}'

# 5. Delete item
curl -X DELETE http://localhost:8080/api/channels/CH1/playlist/items/ITEM_ID \
  -H "Content-Type: application/json" \
  -d '{"clientFingerprint":"current_fingerprint"}'

# 6. Move item
curl -X POST http://localhost:8080/api/channels/CH1/playlist/items/ITEM_ID/move \
  -H "Content-Type: application/json" \
  -d '{"newIndex":5,"clientFingerprint":"current_fingerprint"}'

# 7. Sync check
curl -X POST http://localhost:8080/api/channels/CH1/playlist/sync-check \
  -H "Content-Type: application/json" \
  -d '{"clientFingerprint":"current_fingerprint"}'
```

## Implementation Strategy

### Critical Implementation Points

**1. Atomicity of Shift Operations**
- Fingerprint check + shift + update MUST be atomic
- Spring Data JPA handles transactions at the repository level by default
- For complex operations, ensure service methods handle the full atomic unit
- Use database-level constraints where possible (unique indexes on (channelId, index))

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

### Test Organization

```
src/test/java/com/evertz/playlist/
├── core/                    # Pure unit tests (no Spring context)
│   └── model/               # Domain entity tests
├── application/             # Unit tests with Mockito
│   └── service/             # Service tests with mocked ports
├── infrastructure/          # Framework-dependent tests
│   ├── api/                 # @WebMvcTest - Controller unit tests
│   └── persistence/         # @DataJpaTest - Repository tests
└── integration/             # @SpringBootTest - Full integration tests (E2E)
```

### E2E Test Categories

1. **Ordering invariants** - After each operation, verify contiguous, no gaps, no duplicates
2. **Insert scenarios** - start, middle, end, out-of-range
3. **Delete scenarios** - first, middle, last, non-existent
4. **Move scenarios** - forward, backward, same index, out-of-range
5. **Pagination** - multi-page iteration, completeness, determinism
6. **Fingerprint mismatch** - successful mutation, 409 on stale client

### Test Patterns

**Integration Test Setup:**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaylistIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldReturn409WhenFingerprintMismatch() {
        // Get current fingerprint
        var listResponse = restTemplate.getForObject(
            "/api/channels/CH1/playlist/items",
            PlaylistResponse.class
        );
        String fp1 = listResponse.serverFingerprint();

        // Make a change
        var request1 = new CreatePlaylistItemRequest("Item", 0, fp1);
        restTemplate.postForEntity("/api/channels/CH1/playlist/items", request1, Void.class);

        // Try to use old fingerprint (should fail with 409)
        var request2 = new CreatePlaylistItemRequest("Item2", 0, fp1);
        var response = restTemplate.postForEntity(
            "/api/channels/CH1/playlist/items",
            request2,
            ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
```

**Controller Unit Test:**
```java
@WebMvcTest(PlaylistController.class)
class PlaylistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlaylistService playlistService;

    @Test
    void shouldReturnPlaylistItems() throws Exception {
        when(playlistService.getItems("CH1", 0, 50))
            .thenReturn(new PlaylistResponse(List.of(), 0, "fingerprint"));

        mockMvc.perform(get("/api/channels/CH1/playlist/items")
                .param("limit", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serverFingerprint").value("fingerprint"));
    }
}
```

**Helper method** to verify playlist health:
```java
private void assertPlaylistHealthy(String channelId) {
    var response = restTemplate.getForObject(
        "/api/channels/{channelId}/playlist/items?limit=9999",
        PlaylistResponse.class,
        channelId
    );

    // Assert count matches
    assertThat(response.items()).hasSize(response.totalCount());

    // Assert contiguous (0-based)
    for (int i = 0; i < response.items().size(); i++) {
        assertThat(response.items().get(i).index()).isEqualTo(i);
    }

    // Assert no duplicate indexes
    var indexes = response.items().stream()
        .map(PlaylistItemResponse::index)
        .toList();
    assertThat(indexes).doesNotHaveDuplicates();
}
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
- **Persistence**: H2 embedded database (file-based)
- **Technology stack**: Spring Boot 3.x, Java 21, Spring Data JPA

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

Design decisions documented in ASSUMPTIONS.md:

- [x] 0-based or 1-based indexing? → **0-based**
- [x] Server-generated or client-provided itemIds? → **Server-generated UUID**
- [x] Cursor or offset pagination? → **Offset-based with fingerprint staleness detection**
- [x] What goes into fingerprint computation? → **SHA-256 of ordered index:itemId pairs**
- [x] How to handle insert at index > N? → **400 Bad Request (strict)**
- [x] How to handle move to index > N? → **400 Bad Request (strict)**
- [x] What HTTP status for delete non-existent item? → **404 Not Found**
- [x] Where does clientFingerprint go on DELETE? → **Request body**
- [x] What persistence layer? → **H2 embedded database with Spring Data JPA**
- [x] How to ensure shift operations are atomic? → **Single service method with JPA batch updates**

Technology decisions (RESOLVED):

- [x] Framework → Spring Boot 3.x
- [x] Language → Java 21
- [x] Database → H2 (embedded, file-based)
- [x] ORM → Spring Data JPA
- [x] Testing → JUnit 5 + Mockito + @SpringBootTest + @WebMvcTest
- [x] API Docs → SpringDoc OpenAPI (Swagger UI)
- [x] Exception Handling → @ControllerAdvice with custom exceptions
- [x] Dependency Injection → Constructor injection
- [x] DTOs → Java Records with manual mapping
