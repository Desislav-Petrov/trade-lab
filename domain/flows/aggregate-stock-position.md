# Aggregate Stock Position

## Overview

Covers how the Portfolio domain maintains up-to-date stock position records by consuming `OrderFilledEvent` events published by the Stock Trading domain. Each event triggers an idempotency check followed by either an `OpenPosition` or `UpdatePositionOnBuy` operation on the relevant `Position` row. Both the idempotency record and the position update are written atomically in a single transaction. This flow has no HTTP surface — it is entirely event-driven.

---

## Flow A — Receive and Process OrderFilledEvent (BUY)

The Portfolio domain listener receives an `OrderFilledEvent` from the Stock Trading domain and updates the position projection for the affected account and ticker.

### Actors

- **System**: The Stock Trading domain, which emits `OrderFilledEvent` after a successful order fill.
- **System (Portfolio)**: The Portfolio domain listener and position aggregation service.

### Preconditions

- An `OrderFilledEvent` has been published on the Spring application event bus by the Stock Trading domain.
- The event carries: `orderId`, `accountId`, `userId`, `ticker`, `quantity`, `executionPrice`, `idempotencyKey`, `timestamp`.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Portfolio) | Receive event | The `LedgerEventListener` (or equivalent Portfolio listener class) in `portfolio.messaging` receives the `OrderFilledEvent` via `@TransactionalEventListener(phase = AFTER_COMMIT)`. |
| 2 | System (Portfolio) | Delegate to service | The listener calls `portfolioPositionService.handleOrderFilled(event)`. No business logic in the listener itself. |
| 3 | System (Portfolio) | Check idempotency | The service queries the `portfolio_processed_events` table for the event's `idempotencyKey`. If found, the event is a duplicate — discard silently and return. If not found, continue. |
| 4 | System (Portfolio) | Record idempotency key | Inserts the `idempotencyKey` into `portfolio_processed_events` within the same transaction as the position update (steps 5–7). |
| 5 | System (Portfolio) | Look up existing position | Queries for a `Position` row matching `userId` + `accountId` + `ticker`. |
| 6a | System (Portfolio) | Open new position | If no row exists: creates a new `Position` with `quantity = event.quantity`, `totalCost = event.quantity × event.executionPrice`, `avgPrice = event.executionPrice`, `minPrice = event.executionPrice`, `maxPrice = event.executionPrice`, `lastUpdated = event.timestamp`. |
| 6b | System (Portfolio) | Update existing position | If a row exists: increments `quantity` by `event.quantity`; increments `totalCost` by `event.quantity × event.executionPrice`; recalculates `avgPrice = totalCost / quantity`; updates `minPrice = min(minPrice, event.executionPrice)`; updates `maxPrice = max(maxPrice, event.executionPrice)`; sets `lastUpdated = event.timestamp`. |
| 7 | System (Portfolio) | Commit transaction | The idempotency record insert and the position create/update are committed atomically. If any write fails, the entire transaction rolls back and the event may be redelivered. |

### Postconditions

- A `Position` row exists for the `userId` + `accountId` + `ticker` combination, reflecting the cumulative effect of all processed fills.
- The `idempotencyKey` from this event is recorded in `portfolio_processed_events`.
- No duplicate processing occurs if the event is redelivered.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Duplicate event | `idempotencyKey` already in `portfolio_processed_events` | Event is discarded silently. No position update. No error. |
| Transaction failure | Any DB write in steps 4–6 fails | Full rollback. Event may be redelivered by the application event bus. Position remains at its prior state. |
| Unknown ticker | `ticker` not in supported tickers config | Should not occur — Stock Trading validates the ticker before filling. If it does occur, the service logs an error and discards the event. No position is created. |

---

## Flow B — Receive OrderFilledEvent (SELL) _(future — not triggered in this iteration)_

When the sell flow is introduced, the Portfolio domain will consume `OrderFilledEvent` with `side: SELL`. The listener routes to `portfolioPositionService.handleOrderFilled(event)` identically to Flow A. The service differentiates on `event.side`:

- Decrements `quantity` by `event.quantity`.
- Decrements `totalCost` proportionally.
- Recalculates `avgPrice`.
- Updates `minPrice` / `maxPrice` against `event.executionPrice`.
- If `quantity` reaches zero, retains the row with `quantity = 0`, `totalCost = 0` for audit.

This flow is **out of scope** for this iteration but the `Position` model and idempotency mechanism are designed to support it without schema changes.

---

## Events Consumed

| Event | Source Domain | Trigger |
|-------|--------------|---------|
| `OrderFilledEvent` | Stock Trading | Emitted after a successful order fill (BUY in this iteration) |

> **Note on `OrderFilledEvent` payload extension:** The existing `OrderFilledEvent` payload (`orderId`, `accountId`, `userId`, `ticker`, `quantity`, `executionPrice`, `timestamp`) must be extended with `idempotencyKey` (UUID) to support the Portfolio domain's idempotency check. The `idempotencyKey` is already stored on the `Order` entity and is available at event-emit time. A `side` field (`BUY` | `SELL`) should also be added now to anticipate the sell flow, defaulting to `BUY` for all events in this iteration.

---

## Domain Models Involved

- **Position**: Created at Flow A step 6a or updated at step 6b. All fields written.
- **portfolio_processed_events** (idempotency log): One row inserted per successfully processed event at step 4. Keyed by `idempotencyKey` (UUID, unique constraint). Contains: `idempotencyKey`, `processedAt` (datetime).
