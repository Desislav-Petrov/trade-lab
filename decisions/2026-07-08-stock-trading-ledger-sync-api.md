# Decision: Stock Trading Calls Ledger Synchronously via api/ Interface

**Date:** 2026-07-08  
**Status:** accepted

## Context

The stock buy flow (issue #37) requires two `LedgerEntry` rows to be written atomically
when an order is filled: a `DEBIT / CASH` entry (cash leaving the account) and a
`CREDIT / STOCK_BUY` entry (shares entering the position). These writes are owned by
the Ledger domain. Stock Trading must not import from `ledger.model`, `ledger.repository`,
or `ledger.service` — the architecture standard prohibits cross-domain direct imports.

Two patterns are available: synchronous (`api/` interface) or asynchronous (Spring
Application Event). The fill must be fully consistent — if the ledger write fails, the
order must not be marked `FILLED`. This rules out async event-driven ledger writes, which
would require compensating transactions and add significant complexity for no benefit in
this iteration.

## Decision

The Stock Trading service calls the Ledger domain synchronously using a Kotlin interface
defined in `ledger.api`. The interface exposes a single method (e.g.
`LedgerApi.recordStockBuy(...)`) that creates both ledger entries atomically within the
same database transaction as the order fill. Stock Trading imports only this interface —
never `ledger.model`, `ledger.service`, or `ledger.repository`.

## Consequences

- The Ledger domain must define and implement a `LedgerApi` interface in its `api/`
  package exposing at minimum a `recordStockBuy` operation.
- The Stock Trading service depends on `LedgerApi` via constructor injection.
- The order fill and both ledger entries are committed in a single transaction. If the
  ledger write fails, the entire operation rolls back and the order is not persisted.
- When the Stock Trading domain is extracted into a standalone service, the synchronous
  `LedgerApi` call becomes an HTTP or RPC call to the Ledger service. The service method
  body changes; the interface contract does not.
- A future `recordStockSell` method on `LedgerApi` will follow the same pattern.
