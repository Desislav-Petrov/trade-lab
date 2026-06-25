# AssetSubscription

## Overview

Represents a single user's active subscription to a stock ticker symbol within the Market Data domain. An `AssetSubscription` record is created when a user adds a ticker to their watchlist and deleted when they remove it. The full set of subscribable tickers is provided by a static configuration file in the backend resources folder — it is not stored in the database. Each user holds at most one subscription list for stocks, and each ticker may appear at most once in that list.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| id | uuid | yes | Unique subscription identifier |
| userId | uuid | yes | Reference to the subscribing user |
| ticker | string | yes | 4-letter stock ticker symbol (e.g. `AAPL`). Must exist in the supported tickers configuration. |
| companyName | string | yes | Human-readable company name sourced from the supported tickers configuration at subscription time. |
| createdAt | datetime | yes | Timestamp when the subscription was created |

## Behaviors

- **Subscribe**: Creates a new `AssetSubscription` record for a given `userId` and `ticker`. The `companyName` is resolved from the supported tickers configuration at the time of creation. The `ticker` must exist in the configuration and must not already be subscribed by this user.
- **Unsubscribe**: Permanently deletes the `AssetSubscription` record for a given `userId` and `ticker`. No audit record is kept.
- **BulkSubscribe**: Creates multiple `AssetSubscription` records in a single operation. All-or-nothing: if any ticker in the batch is invalid or already subscribed, the entire batch is rejected.
- **BulkUnsubscribe**: Permanently deletes multiple `AssetSubscription` records for a given `userId` in a single operation. All-or-nothing: if any ticker in the batch is not found in the user's subscriptions, the entire batch is rejected.

## Events

- **AssetSubscribedEvent**: Emitted after a successful subscribe or bulk subscribe. Payload: `userId`, `tickers` (list), `timestamp`. Consumed by the WebSocket feed component to update the in-memory subscription lookup and push an immediate price snapshot to the user's open connection for the new ticker(s).
- **AssetUnsubscribedEvent**: Emitted after a successful unsubscribe or bulk unsubscribe. Payload: `userId`, `tickers` (list), `timestamp`. Consumed by the WebSocket feed component to remove the ticker(s) from the in-memory subscription lookup, stopping further price updates to that user for those ticker(s).

## Relationships

- **User** (`many-to-one`): Each subscription belongs to exactly one user. A user may hold many subscriptions up to the defined maximum.

## Business Rules

- A user may not subscribe to the same ticker more than once. Duplicate subscriptions are rejected with a conflict error.
- A user may hold a maximum of **1000** active subscriptions.
- A ticker must exist in the supported tickers configuration file to be subscribable. Unknown tickers are rejected with a validation error.
- `ticker` and `userId` together form a unique constraint.
- Each user starts with zero subscriptions.
- `companyName` is captured from the configuration at subscription time and stored on the record. It is not re-resolved on reads.
- `AssetSubscription` records are owned by the Market Data domain. No other domain may read or write them directly — cross-domain access must go through the Market Data `api/` interface.
- The supported tickers configuration is static and loaded at application startup. It is not a database entity.
- There is no `updatedAt` field — subscriptions are immutable after creation; the only lifecycle events are create and delete.
- At application startup, all `AssetSubscription` records are loaded from the database to seed the WebSocket feed component's in-memory subscription lookup (keyed by ticker → set of userIds, and by userId → set of tickers).
