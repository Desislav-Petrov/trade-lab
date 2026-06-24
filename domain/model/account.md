# Account

## Overview

Represents a user's paper trading account within the Ledger domain. Holds a stored cash balance denominated in the account's base currency. Every operation that changes the balance also appends an immutable `LedgerEntry` record, giving the account a full audit history. In future iterations the account will also track non-cash holdings (e.g. stock positions) via additional ledger entry types.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| id | uuid | yes | Unique account identifier |
| userId | uuid | yes | Reference to the owning user |
| name | string | yes | Human-readable label. Defaults to `account-{id}` if not supplied at creation. |
| currency | enum | yes | Base cash currency: `USD` \| `GBP` \| `EUR` |
| balance | decimal | yes | Current cash balance in the account's base currency. Always kept in sync with the sum of all cash `LedgerEntry` rows for this account. |
| status | enum | yes | `active` \| `suspended` \| `closed` |
| createdAt | datetime | yes | Timestamp of account creation |
| updatedAt | datetime | yes | Timestamp of last balance change |

## Behaviors

- **Open**: Creates a new account for the owning user. Sets `balance` to `0`, `status` to `active`, and `name` to the supplied label or `account-{id}` if omitted. No `LedgerEntry` rows are created at opening — the history starts empty.
- **TopUp**: Adds a positive whole-number amount to `balance`. The amount must be between 1 and 10,000,000 inclusive (no decimals). The account must be `active`. Appends a `CREDIT` `LedgerEntry` with `assetType: CASH` and `description: "Top-up"`. Updates `updatedAt`. The operation is always accepted instantly — there is no pending or failed state.

## Events

- **AccountOpened**: Emitted after a successful open. Payload: `accountId`, `userId`, `currency`, `name`, `timestamp`.
- **AccountToppedUp**: Emitted after a successful top-up. Payload: `accountId`, `userId`, `amount`, `currency`, `newBalance`, `ledgerEntryId`, `timestamp`.

## Relationships

- **User** (`many-to-one`): An account belongs to exactly one user. A user may hold one or more accounts.
- **LedgerEntry** (`one-to-many`): Every balance-mutating operation (top-up, trade fill, etc.) appends one or more `LedgerEntry` rows to this account.

## Business Rules

- `balance` must never drop below zero.
- `currency` is immutable after account creation.
- `name` is mutable but must not be empty.
- An account must be in `active` status to accept any balance-mutating operation.
- A user may hold multiple accounts, including multiple accounts in the same currency.
- At creation, `balance` is exactly `0` and the ledger history is empty.
- Top-up `amount` must be a positive whole number. Decimals are not permitted.
- Top-up `amount` must not exceed 10,000,000 per operation.
- Top-up currency always matches the account's base `currency`. No FX conversion is applied.
- A top-up may be applied to the same account multiple times. Each operation is independent.
