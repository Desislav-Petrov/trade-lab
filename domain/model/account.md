# Account

## Overview

Represents a user's paper trading account. Holds virtual funds that the user can allocate to simulated trades. The account balance is the single source of truth for the user's available buying power.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| id | uuid | yes | Unique account identifier |
| userId | uuid | yes | Reference to the owning user |
| balance | decimal | yes | Current available virtual funds |
| currency | string | yes | Account currency (e.g. `USD`) |
| status | enum | yes | `active` \| `suspended` \| `closed` |
| createdAt | datetime | yes | Timestamp of account creation |
| updatedAt | datetime | yes | Timestamp of last balance change |

## Behaviors

- **TopUp**: Credits a specified amount to the account balance. Requires the account to be `active` and the amount to be a positive number. Updates `balance` and `updatedAt`.

## Events

- **AccountToppedUp**: Emitted after a successful top-up. Payload: `accountId`, `amount`, `currency`, `balanceAfter`, `timestamp`.

## Relationships

- **User** (`many-to-one`): An account belongs to exactly one user. A user may hold one or more accounts.
- **Transaction** (`one-to-many`): Every top-up produces a transaction record for audit purposes.

## Business Rules

- `balance` must never drop below zero.
- Top-up `amount` must be greater than zero.
- An account must be in `active` status to accept a top-up.
- `currency` is immutable after account creation.
