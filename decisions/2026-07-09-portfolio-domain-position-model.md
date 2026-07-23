# Decision: Portfolio Domain — Position Model, Cross-Domain Communication, and Idempotency Strategy

**Date:** 2026-07-09  
**Status:** accepted

## Context

Issue #39 introduces the Portfolio domain — a horizontal domain responsible for maintaining a real-time materialized view of each user's stock holdings and serving a priced portfolio response to the frontend. Several non-obvious design choices had to be made to keep the domain consistent with existing architecture standards.

### 1. No cash position row stored in Portfolio

The `Account` entity in the Ledger domain already holds an authoritative, always-current `balance` field updated atomically with every ledger write. Maintaining a parallel cash position row in the Portfolio domain would create two sources of truth for the same value, with no benefit and a real risk of drift (e.g. if a `CashMovement` event is delayed or lost).

**Decision:** The Portfolio backend fetches the cash balance synchronously from the Ledger `api/` interface at query time. No cash `Position` row is stored in the Portfolio domain. The cash row is a UI-only construct on the frontend, assembled from the API response.

### 2. Subscribe to existing events — do not introduce new event types

The issue draft proposed two new event types (`StockTradeExecuted`, `CashMovement`). The domain already emits `OrderFilledEvent` (Stock Trading) and `AccountToppedUp` (Ledger) which cover the same semantics. Introducing parallel event types would create dual-publish obligations in the source domains and make the event bus harder to reason about.

**Decision:** The Portfolio domain subscribes to `OrderFilledEvent` from the Stock Trading domain. Since cash is not stored (see point 1), `AccountToppedUp` is not consumed. `OrderFilledEvent` is extended with two fields: `idempotencyKey` (UUID, required for Portfolio's idempotency check) and `side` (`BUY` | `SELL`, to anticipate the sell flow). Both fields are available on the `Order` entity at emit time — no breaking change to existing consumers.

### 3. Idempotency via a processed-events log table

Spring Application Events in a single JVM are synchronous and generally not replayed. However, the architecture standards explicitly anticipate extracting domains into standalone services where the event bus becomes a message broker (e.g. Kafka) with at-least-once delivery semantics. Designing idempotency now avoids a costly retrofit later.

**Decision:** A `portfolio_processed_events` table is introduced, keyed by `idempotencyKey` (UUID, unique constraint). Before any position update, the service checks for the key; if present the event is discarded. The key insertion and position update are committed in the same transaction, making the check-then-act operation atomic and safe under concurrent replay.

### 4. Positions scoped to userId + accountId + ticker

The issue initially suggested `userId` + `ticker` as the position key. A user may hold multiple accounts in different currencies. Aggregating across accounts would merge positions funded by different currencies with no way to distinguish them, making the cash vs. invested breakdown meaningless per account.

**Decision:** Positions are scoped to `userId` + `accountId` + `ticker`. The Portfolio page shows holdings per account, selected via a dropdown. This matches the existing account-centric model in the Ledger and Stock Trading domains.

### 5. Live prices fetched synchronously at query time (bulk)

Storing `currentPrice` in the Position row would require the Portfolio domain to consume the Market Data feed continuously — adding a persistent WebSocket or polling mechanism and another source of stale data risk. The Market Data domain already maintains a fully up-to-date in-memory cache.

**Decision:** The Portfolio service calls the Market Data `api/` interface synchronously at query time, passing all tickers in a single bulk request. The Market Data domain returns the current `currentPrice` for each ticker from its cache. The Portfolio domain does not store current prices.

## Decision

1. No cash `Position` row. Cash is fetched from Ledger `api/` at read time.
2. Portfolio subscribes to `OrderFilledEvent` (extended with `idempotencyKey` and `side`). No new event types introduced.
3. Idempotency enforced via `portfolio_processed_events` table, written atomically with each position update.
4. Position key: `userId` + `accountId` + `ticker`.
5. Live prices fetched in bulk from Market Data `api/` at query time.

## Consequences

- `OrderFilledEvent` (in `stocktrading.messaging`) must be extended with `idempotencyKey: UUID` and `side: OrderSide` (enum: `BUY`, `SELL`). Existing consumers (`OrderFilledEvent` is not currently consumed by any listener other than Stock Trading itself) are unaffected — both new fields are additive.
- A new `portfolio_processed_events` table must be created (JPA entity in `portfolio.model`).
- The Market Data domain must expose a bulk price lookup method on its `api/` interface (e.g. `MarketDataApi.getPrices(tickers: List<String>): Map<String, BigDecimal>`). If this interface does not yet exist, it must be created as part of the Portfolio implementation.
- The Ledger domain must expose an account balance lookup method on its `api/` interface (e.g. `LedgerApi.getBalance(accountId: UUID): AccountBalanceResult`). If not already present, it must be added.
- Zero-quantity positions are retained in the database for audit but excluded from the holdings response (`quantity > 0` filter).
- When the Portfolio domain is extracted into a standalone service, the Spring Application Event subscription becomes a Kafka consumer. The idempotency mechanism requires no change — only the listener class wiring changes.
