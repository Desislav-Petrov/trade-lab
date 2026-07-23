# LedgerEntry

## Overview

An immutable record of a single debit or credit event on an account. Every operation that changes an account's holdings — cash or stock — appends a `LedgerEntry`. The entries form an append-only audit trail. The current cash balance can always be derived by summing all `CASH` entries for that account; the current share position for a ticker can be derived by summing all `STOCK_BUY` and `STOCK_SELL` entries for that account and ticker. In practice the stored `Account.balance` field is the authoritative fast-read source for cash.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| id | uuid | yes | Unique entry identifier |
| accountId | uuid | yes | Reference to the owning account |
| type | enum | yes | `CREDIT` \| `DEBIT` |
| assetType | enum | yes | `CASH` \| `STOCK_BUY` \| `STOCK_SELL` (additional values may be added in future iterations) |
| amount | decimal | yes | Absolute value of the movement. Always positive. For `CASH` entries: the monetary amount in the account's base currency. For `STOCK_BUY` / `STOCK_SELL` entries: the share quantity (number of shares bought or sold). See decision log `decisions/2026-07-08-ledger-entry-amount-stock-semantics.md`. |
| currency | string | yes | ISO 4217 currency code (`USD`, `GBP`, `EUR`). Always the account's base currency. Present on all entry types for consistency. |
| ticker | string | no | Stock ticker symbol (e.g. `AAPL`). Required when `assetType` is `STOCK_BUY` or `STOCK_SELL`. `null` for `CASH` entries. |
| shares | decimal | no | **Deprecated — superseded by `amount` for stock entries.** `null` on all entries going forward. Retained in the schema for backward compatibility only. |
| description | string | no | Human-readable reason for the entry (e.g. `"Top-up"`, `"Buy AAPL x10"`, `"Sell AAPL x5"`). |
| createdAt | datetime | yes | Timestamp of the entry. Immutable. |

## Behaviors

- **Append**: A new `LedgerEntry` is created by the owning flow (e.g. top-up, stock buy, stock sell). Once written it is never modified or deleted.

## Events

_None emitted directly. The parent flow emits the relevant domain event._

## Relationships

- **Account** (`many-to-one`): Each entry belongs to exactly one account.

## Business Rules

- `LedgerEntry` rows are immutable. No update or delete is permitted after creation.
- `amount` must be greater than zero.
- `ticker` must be populated when `assetType` is `STOCK_BUY` or `STOCK_SELL`. It must be `null` when `assetType` is `CASH`.
- For `CASH` entries, `amount` is the monetary value of the movement in the account's base currency.
- For `STOCK_BUY` and `STOCK_SELL` entries, `amount` is the share quantity (number of shares). See decision `2026-07-08-ledger-entry-amount-stock-semantics`.
- A stock buy produces **two** ledger entries in the same atomic operation:
  - `DEBIT / CASH`: `amount` = `quantity × executionPrice`. Cash leaves the account.
  - `CREDIT / STOCK_BUY`: `amount` = share quantity. Shares enter the position.
- A stock sell produces **two** ledger entries in the same atomic operation:
  - `CREDIT / CASH`: `amount` = `quantity × executionPrice`. Cash returns to the account.
  - `DEBIT / STOCK_SELL`: `amount` = share quantity. Shares leave the position.
- A `DEBIT / CASH` entry must not be created if it would cause the account's cash balance to go below zero.
- New `assetType` values require a corresponding decision log entry before being introduced.
