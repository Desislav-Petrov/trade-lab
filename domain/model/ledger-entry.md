# LedgerEntry

## Overview

An immutable record of a single debit or credit event on an account. Every operation that changes an account's holdings — cash or stock — appends a `LedgerEntry`. The entries form an append-only audit trail. The current balance or holding quantity for any instrument can always be derived by summing all entries for that account and instrument type, though in practice the stored `Account.balance` field is the authoritative fast-read source for cash.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| id | uuid | yes | Unique entry identifier |
| accountId | uuid | yes | Reference to the owning account |
| type | enum | yes | `CREDIT` \| `DEBIT` |
| assetType | enum | yes | `CASH` \| `STOCK_BUY` \| `STOCK_SELL` (additional values may be added in future iterations) |
| amount | decimal | yes | Absolute value of the movement. Always positive. |
| currency | string | yes | Currency or instrument identifier. For `CASH` entries this is the ISO 4217 code (`USD`, `GBP`, `EUR`). For `STOCK_BUY` / `STOCK_SELL` entries this is the account's base currency (the cash value of the transaction). |
| ticker | string | no | Stock ticker symbol (e.g. `AAPL`). Required when `assetType` is `STOCK_BUY` or `STOCK_SELL`. `null` for `CASH` entries. |
| shares | decimal | no | Number of shares bought or sold. Required when `assetType` is `STOCK_BUY` or `STOCK_SELL`. Always positive. `null` for `CASH` entries. |
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
- `shares` must be greater than zero when present.
- `ticker` and `shares` must both be populated when `assetType` is `STOCK_BUY` or `STOCK_SELL`. They must both be `null` when `assetType` is `CASH`.
- A `DEBIT` entry must not be created if it would cause the account's derived cash balance to go below zero.
- A `STOCK_BUY` entry has `type: DEBIT` (cash leaves the account). A `STOCK_SELL` entry has `type: CREDIT` (cash returns to the account).
- New `assetType` values require a corresponding decision log entry before being introduced.
