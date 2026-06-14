# Backend Standards

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
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
          model/      # JPA entities, enums, value objects, repository interfaces
          service/    # Business logic, flow orchestration
          api/        # REST controllers, request/response DTOs, domain exceptions
          messaging/  # Domain event definitions, publishers, listeners
          util/       # Domain-scoped utility classes
        ledger/       # same structure
        marketdata/   # same structure
        stocktrading/ # same structure
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

| ID | Layer | What belongs here |
|----|-------|-------------------|
| DB | Database | JPA entity classes and enums in `{domain}.model` |
| REPO | Repository | Spring Data JPA interfaces in `{domain}.model` |
| SVC | Service | Business logic, validation, event emission in `{domain}.service` |
| API | API | Controllers, DTOs, domain exceptions in `{domain}.api` |
| EVT | Event | Domain event classes and listeners in `{domain}.messaging` |

**Rules:**
- No business logic in controllers or repositories.
- No persistence calls in service classes — delegate entirely to repositories.
- `@Transactional` belongs on service methods only.
- REST controller never calls a repository directly 

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
- Primary keys: `UUID` with `@GeneratedValue(strategy = GenerationType.UUID)`.
- All columns mapped explicitly with `@Column`.
- Enums: `@Enumerated(EnumType.STRING)` — never `ORDINAL`.
- Monetary/decimal values: `BigDecimal` with `@Column(precision = 19, scale = 4)`.
- Timestamps: `@CreationTimestamp` for `createdAt`, `@UpdateTimestamp` for `updatedAt`.
- Relationships use standard JPA annotations. Lazy loading is the default.
- `camelCase` Kotlin field names map to `snake_case` columns automatically.

---

## REST API Conventions

- Base path: `/api/v1`
- Resource names are plural nouns: `/api/v1/users`, `/api/v1/accounts`
- Controllers receive and return **DTOs only** — entity classes are never serialised directly.

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

---

## Validation

- Use Jakarta Bean Validation via `spring-boot-starter-validation`.
- Annotations (`@NotBlank`, `@Email`, `@NotNull`) go on request DTO classes.
- Activate with `@Valid` on the controller method parameter.
- A single `@ControllerAdvice` (`GlobalExceptionHandler`) catches all validation and domain exceptions and maps them to error responses.
- Business rule violations (e.g. duplicate email, invalid status transition) are thrown as typed domain exceptions from the service layer.

---

## Error Handling

- One `GlobalExceptionHandler` (`@ControllerAdvice`) lives at the root package (`org.dpp.tradelab`) and handles exceptions from all domains consistently.
- Typed domain exception classes live in `{domain}.api` (e.g. `UserNotFoundException`, `DuplicateEmailException`).
- JPA/Hibernate exceptions must never propagate to the controller — catch and rethrow as domain exceptions in the service layer.

---

## Testing

### Unit tests
- KoTest + `mockito-kotlin`.
- Service tests: mock repositories, assert business rules, exception throwing, and event publishing.
- Controller tests: `@WebMvcTest` + `MockMvc`, mock the service layer, assert status codes and response bodies.
- Test method naming: `methodName_scenario_expectedOutcome`.

### Repository tests
- `@DataJpaTest` with embedded H2.
- Test only custom query methods — not Spring Data built-ins.

### Integration tests
- `@SpringBootTest` with full context and H2 in-memory.
- Reserved for full-stack flow verification. Keep these few; prefer unit tests.

### Coverage expectations
- All service methods must have unit tests covering the happy path and every error case defined in the flow docs.
- All controller endpoints must have `@WebMvcTest` tests covering success and error responses.

---

## Build

- Gradle with Kotlin DSL (`build.gradle.kts`).
- Group: `org.dpp.tradelab` | Artifact: `trade-lab`

| Task | Purpose |
|---|---|
| `./gradlew bootRun` | Start the application |
| `./gradlew test` | Run all tests |
| `./gradlew build` | Compile, test, and package |

---

## Coding Conventions

- Constructor injection everywhere — no `@Autowired` field injection, no `lateinit var` for dependencies.
- DTOs are Kotlin `data class` types.
- Prefer Kotlin nullability (`String?`) over `Optional`. Use `Optional` only where Spring Data JPA requires it (`findById` return type).
- All IDs passed between layers are typed as `UUID`, not `String`.
- `@Transactional(readOnly = true)` on read-only service methods; `@Transactional` on write methods.
