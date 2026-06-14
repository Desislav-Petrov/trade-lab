# Architecture

## Overview

trade-lab is a paper trading platform built as a monolith with enforced domain
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
    model/      # JPA entities, enums, value objects, repository interfaces
    service/    # Business logic, flow orchestration
    api/        # REST controllers, request/response DTOs, domain exceptions
    messaging/  # Domain event definitions, publishers, listeners
    util/       # Domain-scoped utility classes
  ledger/       # same structure
  marketdata/   # same structure
  stocktrading/ # same structure
  config/                     # Global Spring configuration
  GlobalExceptionHandler.kt   # Root level — handles all domains consistently
  TradingLabApplication.kt
```

| Sub-package | What belongs here                                               |
|-------------|------------------------------------------------------------------|
| `model`     | JPA entities, enums, value objects, repository interfaces        |
| `service`   | Business logic, validation, flow orchestration, event emission   |
| `api`       | REST controllers, request/response DTOs, domain exceptions       |
| `messaging` | Domain event data classes, publishers, `@EventListener` handlers |
| `util`      | Utility classes scoped to this domain only                       |

Full per-layer conventions (annotations, transactionality, naming) are defined
in `standards/backend.md`.

---

## Cross-Domain Communication

All communication between domains happens exclusively via Spring Application
Events. No domain may directly import or call a class from another domain.

**Rules:**
- A domain publishes events from its `messaging` package using
  `ApplicationEventPublisher`.
- A domain subscribes to another domain's events using `@EventListener` in its
  own `messaging` package.
- No direct class imports across domain package boundaries.
- No shared JPA entities or repositories across domains. If a domain needs a
  reference to an entity owned by another domain, it stores only the foreign
  ID (`UUID`) — never the entity itself.

This constraint ensures that when a domain is extracted into a standalone
service, the only integration change is replacing Spring events with a message
bus. No business logic changes are required.

---

## Frontend — High-Level Structure

Frontend code lives in `services/front-end/`. Full frontend conventions are
defined in `standards/frontend.md`.

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
  (e.g. Kafka topics, SQS queues).
- No domain logic rewrite is required. Extraction is a packaging and deployment
  change only.
