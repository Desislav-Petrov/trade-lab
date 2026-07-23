# Position

## Overview

Represents the aggregated stock holding for a single ticker symbol within one account, owned by the Portfolio domain. A `Position` row is created the first time a stock buy order is filled for a given `userId` + `accountId` + `ticker` combination, and updated on every subsequent fill for that combination. It is never created for cash — the cash balance is the authoritative value held by the Ledger domain and is fetched at query time via the Ledger `api/` interface.

The Portfolio domain consumes `OrderFilledEvent` from the Stock Trading domain to keep positions current. Idempotency is enforced by storing the `idempotencyKey` from each processed event; replayed events are silently discarded.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| id | uuid | yes | Unique position identifier |
| userId | uuid | yes | Reference to the owning user |
| accountId | uuid | yes | Reference to the account that funded the trades. Foreign key stored as UUID only — no JPA join to the Ledger domain. |
| ticker | string | yes | 4-letter stock ticker symbol (e.g. `AAPL`). Must exist in the supported tickers configuration. |
| assetType | enum | yes | `STOCK` — only value in this iteration. `CRYPTO` may be added in future iterations with a corresponding decision log entry. |
| quantity | decimal | yes | Current number of shares held. Sum of all buy quantities minus sum of all sell quantities for this position. Always ≥ 0. |
| totalCost | decimal | yes | Running sum of `quantity × executionPrice` for all buy fills on this position. Used to derive `avgPrice`. Decremented on sell fills proportionally. |
| avgPrice | decimal | yes | Average cost per share: `totalCost / quantity`. Recalculated on every update. `null` is never permitted while `quantity > 0`. |
| minPrice | decimal | yes | Lowest `executionPrice` ever recorded across all fills (buys and sells) for this position. Set on first fill; updated only when a new fill price is lower. |
| maxPrice | decimal | yes | Highest `executionPrice` ever recorded across all fills (buys and sells) for this position. Set on first fill; updated only when a new fill price is higher. |
| lastUpdated | datetime | yes | Timestamp of the last position update. Set to the `timestamp` from the consuming event. |

## Behaviors

- **OpenPosition**: Creates a new `Position` record when the first `OrderFilledEvent` (side `BUY`) is received for a `userId` + `accountId` + `ticker` combination. Sets `quantity`, `totalCost`, `avgPrice`, `minPrice`, and `maxPrice` from the event's `quantity` and `executionPrice`.
- **UpdatePositionOnBuy**: Increments `quantity` by the fill `quantity`. Increments `totalCost` by `quantity × executionPrice`. Recalculates `avgPrice = totalCost / quantity`. Updates `minPrice` and `maxPrice` if the new `executionPrice` is outside the current range. Updates `lastUpdated`.
- **UpdatePositionOnSell** _(future — not triggered in this iteration)_: Decrements `quantity` by the fill `quantity`. Decrements `totalCost` proportionally (`totalCost × (soldQuantity / previousQuantity)`). Recalculates `avgPrice`. Updates `minPrice` and `maxPrice` if applicable. If `quantity` reaches zero, the position row is **retained** (not deleted) with `quantity = 0` and `totalCost = 0` for audit purposes. Updates `lastUpdated`.
- **DiscardDuplicate**: If an incoming `OrderFilledEvent` carries an `idempotencyKey` already recorded in the processed-events log, the event is silently discarded. No position update is made.

## Events

_None emitted directly. The Portfolio domain is a pure consumer in this iteration._

## Relationships

- **User** (`many-to-one`): Each position belongs to exactly one user. Reference by `userId` (UUID) only.
- **Account** (`many-to-one`): Each position is funded by exactly one account. Reference by `accountId` (UUID) only — no JPA join to the Ledger domain.

## Business Rules

- `userId` + `accountId` + `ticker` form a unique constraint. At most one active `Position` row exists per combination.
- `quantity` must never be negative.
- `totalCost` must never be negative.
- `avgPrice` is always `totalCost / quantity` when `quantity > 0`. It must not be stored as a stale value — it is recalculated on every write.
- `minPrice` and `maxPrice` are derived from execution prices of fills, not from live market data.
- A `Position` row with `quantity = 0` is retained after a full sell-out for audit purposes. It must not appear in the active holdings view returned to the frontend (filter: `quantity > 0`).
- Idempotency is enforced by a separate processed-events log table (`portfolio_processed_events`) keyed by `idempotencyKey`. Before any position update the listener checks this table; if the key is present the event is discarded; if absent the key is inserted and the position update proceeds — both in the same transaction.
- `assetType` is currently always `STOCK`. Adding a new value requires a corresponding decision log entry.
- The Portfolio domain must not import from `ledger.model`, `ledger.repository`, `ledger.service`, `stocktrading.model`, `stocktrading.repository`, or `stocktrading.service`. All cross-domain reads go through `api/` interfaces.
