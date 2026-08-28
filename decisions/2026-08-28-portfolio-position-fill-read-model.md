# Decision: Portfolio Owns a PositionFill Read Model for Fill History

**Date:** 2026-08-28
**Status:** accepted

## Context

Issue #146 requires an Advanced Insights chart showing the full trade price history (BUY and SELL execution prices over time) for each stock symbol in an account. The raw data for this chart — `executionPrice`, `quantity`, `side`, and `timestamp` per fill — exists on the `Order` entity in the Stock Trading domain.

Two options were considered:

1. **Query Stock Trading at read time**: The Portfolio backend calls into the Stock Trading domain (via a new `api/` interface) to fetch order history when the Advanced Insights endpoint is hit.
2. **Portfolio persists its own read model**: The Portfolio domain stores each individual fill as a `PositionFill` entity, populated by consuming the existing `OrderFilledEvent` that Portfolio already listens to for `Position` maintenance.

## Decision

Option 2 — Portfolio persists a `PositionFill` read model.

The Portfolio domain already consumes `OrderFilledEvent` to maintain aggregated `Position` state. In the same event handler, for no additional cross-domain coupling, it can persist a `PositionFill` row containing the data needed for the chart. This is consistent with the event-driven read model pattern implied by the architecture: domains maintain their own queryable state from events rather than reaching into sibling domains at query time.

Option 1 is rejected because:
- It introduces a synchronous cross-domain dependency at query time (Portfolio → Stock Trading), violating the principle that horizontals must not create runtime coupling to verticals.
- It requires Stock Trading to expose an order-history API that serves Portfolio's presentation concerns — a responsibility mismatch.
- It makes the Portfolio holdings endpoint slower and harder to scale independently.

## Consequences

- A new `PositionFill` entity is added to the Portfolio domain (see `domain/model/position-fill.md`).
- The existing `OrderFilledEvent` listener in `portfolio.messaging` is extended to insert a `PositionFill` row alongside the existing `Position` update, within the same transaction.
- Idempotency is enforced via the existing `portfolio_processed_events` log — a single log entry per event covers both the `Position` update and the `PositionFill` insert.
- A new endpoint `GET /api/v1/portfolio/fills?accountId={accountId}` is added to the Portfolio domain to serve fill history to the frontend.
- `PositionFill` rows are immutable and retained indefinitely in this iteration. No archival or retention policy is defined yet.
- The Stock Trading domain requires no changes.
