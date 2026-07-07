# Decision: Extend LedgerEntry with Stock Transaction Types

**Date:** 2026-07-07  
**Status:** accepted

## Context

Issue #33 (Transaction list for user accounts) requires the transaction list to display stock buy and sell entries alongside cash entries. The current `LedgerEntry` model explicitly reserves `assetType: STOCK` for future use and states that no flow in the current iteration may create a `STOCK` entry. It has no `ticker` or `shares` fields.

To support the transaction list — and to lay the groundwork for the stock trading vertical — `LedgerEntry` must be extended now.

## Decision

1. Replace the single reserved `STOCK` value in the `assetType` enum with two concrete values: `STOCK_BUY` and `STOCK_SELL`. The generic `STOCK` value is removed.
2. Add two nullable fields to `LedgerEntry`: `ticker` (string, the stock ticker symbol) and `shares` (decimal, the number of shares). Both are `null` for `CASH` entries and required for `STOCK_BUY` / `STOCK_SELL` entries.
3. The constraint "no flow in the current iteration may create a `STOCK` entry" is lifted. Stock entries will be created by the stock trading vertical (future buy/sell flows).

## Consequences

- `domain/model/ledger-entry.md` is updated to reflect the new enum values and fields.
- The stock trading vertical must populate `ticker` and `shares` when creating `STOCK_BUY` or `STOCK_SELL` entries.
- The transaction list flow and use case (issue #33) can now reference all four entry types.
- Any future asset types (e.g. crypto) should follow the same pattern: a new `assetType` enum value and additional nullable fields as needed, with a corresponding decision log entry.
