# Backend Standards

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| JDK | 21 |
| Framework | Spring Boot `4.1.0` |
| ORM | Hibernate (via Spring Data JPA) |
| Database | H2 (relational, in-memory) |
| Unit testing | KoTest + Mockito (`mockito-kotlin`) |
| Build | Gradle (Kotlin DSL — `build.gradle.kts`) |
| API | REST (JSON over HTTP) |
| Minimum JDK | 21 |

Dependency versions for Kotlin, Hibernate, and Spring Framework are managed by the Spring Boot BOM — do not declare them manually unless overriding.

---

## Project Structure

```
src/
  main/
    kotlin/
      org/dpp/tradelab/
        user/
          model/        # JPA entities and enums only
          repository/   # Spring Data JPA repository interfaces
          service/      # Business logic, flow orchestration
          controller/   # REST controllers (implement generated ApiDelegate interfaces)
          exception/    # Domain-specific exception classes
          api/          # Kotlin interfaces exposed to other domains (cross-domain sync only)
          messaging/    # Domain event definitions, publishers, listeners
          util/         # Domain-scoped utility classes
        ledger/         # same structure
        marketdata/     # same structure
        stocktrading/   # same structure
        config/                     # Global Spring configuration
        GlobalExceptionHandler.kt   # Root level — handles all domains
        TradingLabApplication.kt
    resources/
      application.yml
  test/
    kotlin/
      org/dpp/tradelab/
        user/
        ledger/
        marketdata/
        stocktrading/
```

One production class per file. One test class per production class.

---

## Layer Responsibilities

These IDs match those used by the decomposer agent.

| ID | Layer | Package | What belongs here |
|----|-------|---------|-------------------|
| DB | Database | `{domain}.model` | JPA entity classes and enums |
| REPO | Repository | `{domain}.repository` | Spring Data JPA interfaces |
| SVC | Service | `{domain}.service` | Business logic, validation, event emission |
| CONTROLLER | Controller | `{domain}.controller` | REST controllers — implement generated `{Domain}ApiDelegate` |
| EXCEPTION | Exception | `{domain}.exception` | Domain-specific exception classes |
| API | API | `{domain}.api` | Kotlin interfaces exposed to other domains (cross-domain sync only) |
| EVT | Event | `{domain}.messaging` | Domain event classes and listeners |

**Rules:**
- No business logic in controllers or repositories.
- No persistence calls in service classes — delegate entirely to repositories.
- `@Transactional` belongs on service methods only.
- REST controller never calls a repository directly.
- Other domains may only import from `{domain}.api` — never from `{domain}.model`, `{domain}.repository`, or `{domain}.service`.
- DTOs (request/response models) are **never hand-written** — they are generated from the domain's OpenAPI contract (see OpenAPI Code Generation section below).

---

## Database

- H2 in-memory, auto-configured by Spring Boot.
- `ddl-auto: create-drop` — schema generated from JPA entities on startup.
- `h2.console.enabled: true` in development for inspection.
- All `datetime` fields stored in UTC.
- UUIDs used as primary keys for all entities.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:tradelab;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
  h2:
    console:
      enabled: true
```

---

## ORM — Entity Mapping

- Entity classes in `{domain}.model`, annotated `@Entity`.
- Entity classes are plain `class` — **never `data class`**.
- Primary keys: `UUID`, pre-assigned by the service layer via `UUID.randomUUID()` before entity construction. **Do not use `@GeneratedValue`.**
- All entity classes implement `org.springframework.data.domain.Persistable<UUID>` with a `@Transient _isNew: Boolean = true` flag. This forces Spring Data to call `EntityManager.persist()` (INSERT) instead of `merge()` (UPDATE then INSERT) for new entities with a pre-assigned id.
- `equals` and `hashCode` are implemented manually based on `id` only.
- `toString` is implemented manually.
- All columns mapped explicitly with `@Column`.
- Enums: `@Enumerated(EnumType.STRING)` — never `ORDINAL`.
- Monetary/decimal values: `BigDecimal` with `@Column(precision = 19, scale = 4)`.
- Timestamps: `@CreationTimestamp` for `createdAt`, `@UpdateTimestamp` for `updatedAt`.
- Relationships use standard JPA annotations. Lazy loading is the default.
- `camelCase` Kotlin field names map to `snake_case` columns automatically.

### Entity template

```kotlin
@Entity
@Table(name = "things")
class Thing(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID,

    // ... other fields ...

    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {
    override fun getId(): UUID = id
    override fun isNew(): Boolean = _isNew
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Thing) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "Thing(id=$id, ...)"
}
```

---

## OpenAPI Code Generation

Every domain's request/response DTOs and API delegate interface are **generated** from the domain's OpenAPI contract. They are never hand-written.

### Contract location

```
services/contract/{domain}-openapi.yaml
```

### Generator

The `openapi-generator` Gradle plugin (`org.openapi.generator`) runs as part of `compileKotlin`. It uses the `kotlin-spring` generator with `useSpringBoot3=true` and `delegatePattern=true`.

Configuration in `build.gradle.kts` (one block per domain):

```kotlin
openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set("$rootDir/../../services/contract/{domain}-openapi.yaml")
    outputDir.set("$buildDir/generated/{domain}")
    apiPackage.set("org.dpp.tradelab.{domain}.generated.api")
    modelPackage.set("org.dpp.tradelab.{domain}.generated.model")
    configOptions.set(mapOf(
        "useSpringBoot3" to "true",
        "delegatePattern" to "true",
        "serializationLibrary" to "jackson"
    ))
}
```

Generated sources are on the compile classpath. They are **not committed to git** (`build/` is in `.gitignore`).

### What gets generated

| Artefact | Location | Description |
|----------|----------|-------------|
| Request/response `data class` models | `{domain}.generated.model` | e.g. `RegisterUserRequest`, `RegisterUserResponse` |
| `{Domain}ApiDelegate` interface | `{domain}.generated.api` | One method per operation — the controller implements this |
| `{Domain}ApiController` | `{domain}.generated.api` | Spring `@RestController` stub that delegates to the `ApiDelegate` |

### Controller pattern

The hand-written controller lives in `{domain}.controller` and implements the generated delegate:

```kotlin
@Service
class UserApiDelegateImpl(private val userService: UserService) : UserApiDelegate {
    override fun registerUser(registerUserRequest: RegisterUserRequest): ResponseEntity<RegisterUserResponse> {
        val userId = userService.registerUser(
            firstName = registerUserRequest.firstName,
            lastName = registerUserRequest.lastName,
            address = registerUserRequest.address,
            email = registerUserRequest.email
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterUserResponse(userId = userId.toString()))
    }
}
```

The generated `{Domain}ApiController` is the `@RestController` — it is not hand-written. The delegate class (`{Domain}ApiDelegateImpl`) in `{domain}.controller` contains all implementation logic.

---

## REST API Conventions

- Base path: `/api/v1`
- Resource names are plural nouns: `/api/v1/users`, `/api/v1/accounts`
- Controllers receive and return generated DTO types only — entity classes are never serialised directly.

### HTTP methods and status codes

| Operation | Method | Success status |
|---|---|---|
| Create resource | POST | 201 Created |
| Read resource(s) | GET | 200 OK |
| Full update | PUT | 200 OK |
| Partial update | PATCH | 200 OK |
| Delete resource | DELETE | 204 No Content |

### Error status codes

| Scenario | Status |
|---|---|
| Validation failure | 400 Bad Request |
| Not found | 404 Not Found |
| Conflict (e.g. duplicate email) | 409 Conflict |
| Server error | 500 Internal Server Error |

### Error response shape

```json
{
  "status": 400,
  "error": "Validation failed",
  "details": ["email must be a valid email address", "firstName must not be blank"]
}
```

### OpenAPI contract

Each domain owns exactly one OpenAPI 3.0.3 contract file at `services/contract/{domain}-openapi.yaml`. This file is the single source of truth for all request/response shapes, HTTP methods, paths, and error contracts for that domain.

- `info.title` must be `Trade Lab API — {Domain}`.
- A domain's YAML contains only paths belonging to that domain.
- When adding a new use case, append new paths and schemas — never overwrite.
- The generated `{Domain}ApiController` + `{Domain}ApiDelegate` are the sole HTTP entry point for the domain.

---

## Validation

- Bean Validation annotations (`@NotBlank`, `@Email`, `@NotNull`) are placed in the OpenAPI YAML (`x-constraints` or via pattern/minLength) so the generator emits them on the generated model classes.
- A single `@ControllerAdvice` (`GlobalExceptionHandler`) catches all validation and domain exceptions and maps them to error responses.
- Business rule violations are thrown as typed domain exceptions from the service layer.

---

## Error Handling

- One `GlobalExceptionHandler` (`@ControllerAdvice`) lives at the root package (`org.dpp.tradelab`) and handles exceptions from all domains consistently.
- Typed domain exception classes live in `{domain}.exception` (e.g. `UserNotFoundException`, `DuplicateEmailException`).
- JPA/Hibernate exceptions must never propagate to the controller — catch and rethrow as domain exceptions in the service layer.

---

## Domain Events

Event classes are plain Kotlin `data class` types in `{domain}.messaging`.

**Naming:** `{Entity}{Action}Event` — e.g. `UserRegisteredEvent`, `AccountToppedUpEvent`.
Event names must match those defined in the flow docs exactly.

**Payload:** carry only what subscribers need. For cross-domain events, prefer IDs over full entity objects — never include a JPA entity in an event payload.

**Publishing** — inject `ApplicationEventPublisher` into the service class:

```kotlin
eventPublisher.publishEvent(UserRegisteredEvent(userId = user.id, email = user.email, timestamp = Instant.now()))
```

**Subscribing** — `@EventListener` method in the receiving domain's `messaging` package:

```kotlin
@EventListener
fun on(event: UserRegisteredEvent) { ... }
```

Use `@TransactionalEventListener(phase = AFTER_COMMIT)` when the listener must only run after the publishing transaction has committed successfully.

Events are synchronous by default. Introducing async (`@Async`) requires a decision log entry.

---

## Testing

### Unit tests
- KoTest + `mockito-kotlin`.
- Service tests: mock repositories, assert business rules, exception throwing, and event publishing.
- Delegate (controller) tests: `@SpringBootTest` + `@AutoConfigureMockMvc` + `MockMvc`, mock the service layer, assert HTTP status codes and response bodies.
- Test method naming: `methodName_scenario_expectedOutcome`.

### Repository tests
- `@SpringBootTest` + `@AutoConfigureTestEntityManager` + `@Transactional` with embedded H2.
- Test only custom query methods — not Spring Data built-ins.

### Integration tests
- `@SpringBootTest` with full context and H2 in-memory.
- Reserved for full-stack flow verification. Keep these few; prefer unit tests.

### Coverage expectations
- All service methods must have unit tests covering the happy path and every error case defined in the flow docs.
- All delegate methods must have `@SpringBootTest`+MockMvc tests covering success and error responses.

---

## Build

- Gradle with Kotlin DSL (`build.gradle.kts`).
- Group: `org.dpp.tradelab` | Artifact: `trade-lab`

| Task | Purpose |
|---|---|
| `./gradlew bootRun` | Start the application |
| `./gradlew test` | Run all tests |
| `./gradlew build` | Compile, test, and package |
| `./gradlew openApiGenerate` | Re-run OpenAPI code generation |

---

## Coding Conventions

- Constructor injection everywhere — no `@Autowired` field injection, no `lateinit var` for dependencies.
- Generated DTOs are used directly — never re-wrap or copy into hand-written data classes.
- Prefer Kotlin nullability (`String?`) over `Optional`. Use `Optional` only where Spring Data JPA requires it (`findById` return type).
- All IDs passed between layers are typed as `UUID`, not `String`.
- `@Transactional(readOnly = true)` on read-only service methods; `@Transactional` on write methods.
