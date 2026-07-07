# Architecture

## Overview

**Trade-lab** is a paper trading platform built as a modular monolith with enforced domain
boundaries. It is designed to deliver fast iteration as a single deployable unit
while keeping domain responsibilities isolated enough to extract individual
services in the future without a rewrite.

---

## Domain Taxonomy

Domains are classified as horizontals or verticals.

### Horizontals

Horizontals are foundational domains that serve the entire platform. Any
vertical may depend on them.

| Domain      | Responsibility                                                  |
|-------------|-----------------------------------------------------------------|
| User        | User identity, registration, login, and lifecycle               |
| Ledger      | Account holdings — money, stocks, and any other assets          |
| Market Data | Sourcing and serving market data per product type               |

### Verticals

Verticals are product-specific trading experiences. Each vertical depends on
one or more horizontals but is otherwise self-contained.

| Domain        | Responsibility                                                |
|---------------|---------------------------------------------------------------|
| Stock Trading | Simulated equity trading — orders, positions, trade history   |

Future verticals may include Crypto Trading, Options Trading, and others.

**Dependency direction:** verticals may depend on horizontals. Horizontals must
never depend on verticals.

---

## Backend — Domain Package Structure

The backend is a single Spring Boot application rooted at `org.dpp.tradelab`.
Each domain is a top-level sub-package with a fixed internal structure.

```
org/dpp/tradelab/
  user/
    model/        # JPA entities and enums only
    repository/   # Spring Data JPA repository interfaces
    service/      # Business logic, flow orchestration
    controller/   # REST delegate implementations (implement generated ApiDelegate)
    exception/    # Domain-specific exception classes
    api/          # Kotlin interfaces exposed to other domains (cross-domain sync only)
    messaging/    # Domain event data classes, publishers, and listener classes
    util/         # Domain-scoped utility classes
  ledger/         # same structure
  marketdata/     # same structure
  stocktrading/   # same structure
  config/                     # Global Spring configuration
  GlobalExceptionHandler.kt   # Root level — handles all domains consistently
  TradingLabApplication.kt
```

| Sub-package | What belongs here |
|-------------|-------------------|
| `model` | JPA entities and enums |
| `repository` | Spring Data JPA repository interfaces |
| `service` | Business logic, validation, flow orchestration, event emission |
| `controller` | REST delegate implementations — implement generated `{Domain}ApiDelegate` |
| `exception` | Domain-specific exception classes |
| `api` | Kotlin interfaces exposed to other domains (cross-domain sync only) |
| `messaging` | Domain event `data class` types, `ApplicationEventPublisher` calls, and `@Component` listener classes containing `@EventListener` methods |
| `util` | Utility classes scoped to this domain only |

> **Listener class rule:** `@EventListener` and `@TransactionalEventListener` annotations are **never** placed on service class methods. They belong exclusively in a dedicated `@Component` listener class inside `{domain}.messaging`. Each listener method calls exactly one `handle*` method on the relevant service. See `standards/backend.md` — Domain Events for the full pattern and rationale.

Request/response DTOs are generated from `services/contract/{domain}-openapi.yaml` — they are never hand-written. See `standards/backend.md` for full OpenAPI generation details.

Full per-layer conventions (annotations, transactionality, naming) are defined in `standards/backend.md`.

---

## Cross-Domain Communication

Two patterns are permitted. Everything else is forbidden.

### Sync — Kotlin interfaces in `api/`

When a domain needs a synchronous response from another domain (e.g. a balance
check before placing an order), it depends on a Kotlin interface defined in the
source domain's `api/` package.

- The source domain defines the interface in `{domain}.api` — this is its
  published contract.
- The source domain's service layer implements the interface.
- The consuming domain imports only the interface — never the implementation,
  never anything from `{domain}.model` or `{domain}.service`.

### Async — Spring Application Events in `messaging/`

When a domain needs to react to something that happened in another domain
without requiring a response, it uses Spring Application Events.

- The source domain publishes events from its service layer using
  `ApplicationEventPublisher`.
- The consuming domain subscribes in a dedicated `@Component` listener class
  inside its own `messaging/` package. The listener method calls a `handle*`
  method on the relevant service — it contains no business logic itself.

### Rules that apply to both patterns

- No imports from `{domain}.model` or `{domain}.service` across domain
  boundaries — ever.
- No shared JPA entities or repositories across domains. Foreign references are
  stored as `UUID` only — never as the entity itself.
- No imports from another domain's `messaging/` package — subscribe to events
  by type only; Spring resolves the listener automatically.

These constraints ensure that extracting a domain into a standalone service
requires only deployment and wiring changes — no business logic rewrite.

---

## Frontend — High-Level Structure

Frontend code lives in `services/front-end/`. Full frontend conventions are
defined in `standards/frontend.md`.

The frontend communicates with the backend exclusively via REST (JSON over HTTP).
The API contract is defined in OpenAPI 3.0 and lives in
`services/contract/trade-lab-openapi.yaml` — the single source of truth for all
endpoint definitions consumed by the frontend.

The frontend domain structure mirrors the backend domain taxonomy.

```
services/front-end/
  src/
    domains/
      user/           # Registration, login
      ledger/         # Account balances, holdings
      marketdata/     # Price feeds, instrument search
      stocktrading/   # Order placement, portfolio view
    shared/           # Shared components, API client, utilities
    app/              # Root configuration, routing, layout
```

---

## Future Service Extraction

When a domain is ready to be extracted into a standalone service:

- Its `model`, `service`, `api`, and `messaging` sub-packages become the new
  service's source tree with minimal restructuring.
- Spring Application Event contracts in `messaging` become message bus contracts
  (e.g. Kafka topics, SQS queues). Because listener logic is isolated in
  dedicated listener classes (not embedded in service classes), only the
  listener classes need to change — the service `handle*` methods remain intact.
- No domain logic rewrite is required. Extraction is a packaging and deployment
  change only.
