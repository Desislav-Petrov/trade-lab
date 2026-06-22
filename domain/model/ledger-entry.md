# LedgerEntry

## Overview

An immutable record of a single debit or credit event on an account. Every operation that changes an account's holdings — cash or, in future, stock positions — appends a `LedgerEntry`. The entries form an append-only audit trail. The current balance or holding quantity for any instrument can always be derived by summing all entries for that account and instrument type, though in practice the stored `Account.balance` field is the authoritative fast-read source for cash.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| id | uuid | yes | Unique entry identifier |
| accountId | uuid | yes | Reference to the owning account |
| type | enum | yes | `CREDIT` \| `DEBIT` |
| assetType | enum | yes | `CASH` \| `STOCK` (only `CASH` is used in the current iteration) |
| amount | decimal | yes | Absolute value of the movement. Always positive. |
| currency | string | yes | Currency or instrument identifier. For `CASH` entries this is the ISO 4217 code (`USD`, `GBP`, `EUR`). For future `STOCK` entries this will be the ticker symbol. |
| description | string | no | Human-readable reason for the entry (e.g. `"Top-up"`, `"Buy AAPL x10"`). |
| createdAt | datetime | yes | Timestamp of the entry. Immutable. |

## Behaviors

- **Append**: A new `LedgerEntry` is created by the owning flow (e.g. top-up, trade fill). Once written it is never modified or deleted.

## Events

_None emitted directly. The parent flow emits the relevant domain event._

## Relationships

- **Account** (`many-to-one`): Each entry belongs to exactly one account.

## Business Rules

- `LedgerEntry` rows are immutable. No update or delete is permitted after creation.
- `amount` must be greater than zero.
- `assetType` of `STOCK` is reserved for future use. No flow in the current iteration may create a `STOCK` entry.
- A `DEBIT` entry must not be created if it would cause the account's derived cash balance to go below zero.
