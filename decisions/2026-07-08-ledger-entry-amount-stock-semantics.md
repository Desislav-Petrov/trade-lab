# Decision: LedgerEntry.amount Holds Share Quantity for Stock Entries

**Date:** 2026-07-08  
**Status:** accepted

## Context

The existing `domain/model/ledger-entry.md` defined `amount` for `STOCK_BUY` and
`STOCK_SELL` entries as "the cash value of the transaction" in the account's base
currency. This was written before the stock buy flow was designed and reflected an
assumption that was never validated against a real trade use case.

When designing the stock buy flow (issue #37), it became clear that the two ledger
rows written on a fill are:

1. A `DEBIT / CASH` entry — cash leaving the account. `amount` = `quantity × executionPrice`.
2. A `CREDIT / STOCK_BUY` entry — shares entering the position. `amount` = share quantity.

Storing the cash value on the `STOCK_BUY` entry would duplicate information already
captured on the corresponding `DEBIT / CASH` row, and would conflate two different
units (currency vs. shares) in a single field. Share quantity is the meaningful value
for position reconstruction and is what subscribers downstream need.

## Decision

For `STOCK_BUY` and `STOCK_SELL` entries, `LedgerEntry.amount` holds the **share
quantity** (number of shares bought or sold), not the cash value. The cash value
of the transaction is always captured on the corresponding `DEBIT` or `CREDIT`
`CASH` entry written in the same atomic operation.

`domain/model/ledger-entry.md` has been updated to reflect this.

## Consequences

- Any code or documentation that assumed `amount` on stock entries represented a
  cash value must be revised.
- Position reconstruction from the ledger is straightforward: sum `amount` across
  all `STOCK_BUY` credits and `STOCK_SELL` debits for a given `ticker` and
  `accountId`.
- The cash cost of a trade is always reconstructable from the paired `CASH` `DEBIT`
  entry — no information is lost.
