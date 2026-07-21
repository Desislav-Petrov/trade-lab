# MarketDataSnapshot

## Overview

Represents the latest known price data for a single ticker symbol, held exclusively in the backend's in-memory cache within the Market Data domain. A snapshot entry is created or overwritten each time the price feed generator emits data for a symbol. It is never persisted to the database. On WebSocket connection, the backend uses this cache to push an initial snapshot to the connecting user for every ticker they are subscribed to. Subsequent feed updates overwrite the relevant entry and are pushed in real time to all connected users subscribed to that ticker.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| ticker | string | yes | 4-letter stock ticker symbol (e.g. `AAPL`). Acts as the cache key. |
| companyName | string | yes | Human-readable company name sourced from the supported tickers configuration. |
| currentPrice | decimal | yes | Latest traded price in USD. 3 decimal places. |
| open | decimal | yes | Seed price set once at application startup for this ticker. Never changes within a session. 3 decimal places. |
| dayLow | decimal | yes | Lowest `currentPrice` recorded for this ticker since application startup. Updated on every tick when the new price is lower than the current `dayLow`. 3 decimal places. |
| dayHigh | decimal | yes | Highest `currentPrice` recorded for this ticker since application startup. Updated on every tick when the new price is higher than the current `dayHigh`. 3 decimal places. |
| fiftyTwoWeekHigh | decimal | yes | Highest `currentPrice` recorded for this ticker since application startup. Updated on every tick when the new price is higher than the current `fiftyTwoWeekHigh`. 3 decimal places. Semantically distinct from `dayHigh` but holds the same value in this simulation as there is no persistent price history between sessions. |
| updatedAt | datetime | yes | Timestamp of the last feed update that wrote to this entry. |

## Behaviors

- **Seed**: At application startup, the `PriceFeedGenerator` generates an initial `currentPrice` for every supported ticker by drawing a random fractional value uniformly between **$200.000 and $400.000** (3 decimal places). This seed price is written to `currentPrice`, `open`, `dayLow`, `dayHigh`, and `fiftyTwoWeekHigh` simultaneously. The cache is fully populated before any WebSocket connections are accepted.
- **Update**: Each time the price feed generator emits a tick for a ticker, the new `currentPrice` is calculated by applying a random percentage change to the previous `currentPrice`. The direction (up or down) is chosen with equal 50/50 probability. The magnitude is drawn uniformly at random from the range **0.5% to 1.5%** of the previous price. The result is rounded to 3 decimal places. After computing the new `currentPrice`: `dayLow` is updated if the new price is lower; `dayHigh` and `fiftyTwoWeekHigh` are updated if the new price is higher. `open` is never modified after seeding. `updatedAt` is set to the current timestamp.
- **Snapshot**: When a user establishes a WebSocket connection, the backend reads all cache entries for the tickers the user is subscribed to and sends them as a single snapshot message.

## Events

_No domain events are emitted directly by this entity. It is an internal cache structure. Feed updates trigger WebSocket push messages, not application events._

## Relationships

- **AssetSubscription** (`reference`): The subscription lookup is used to determine which `MarketDataSnapshot` entries are relevant for a given connected user. `MarketDataSnapshot` does not own or reference `AssetSubscription` directly — the relationship is resolved at runtime by the WebSocket feed component.

## Business Rules

- `MarketDataSnapshot` is in-memory only. It is never written to the database.
- The cache is keyed by `ticker`. There is at most one entry per ticker at any time.
- All price fields (`currentPrice`, `open`, `dayLow`, `dayHigh`, `fiftyTwoWeekHigh`) are positive decimals rounded to 3 decimal places.
- The cache is fully initialised at application startup before any WebSocket connections are accepted.
- The `PriceFeedGenerator` seeds each ticker with an initial `currentPrice` drawn uniformly at random from the range **$200.000–$400.000** (inclusive, fractional, 3 decimal places).
- On each tick, the price change direction is a 50/50 random choice (increment or decrement). The magnitude is drawn uniformly at random from **0.5%–1.5%** of the previous `currentPrice`, rounded to 3 decimal places.
- `open` is set at seed time and is immutable for the lifetime of the application session.
- `dayLow` is initialised to the seed price and only ever decreases.
- `dayHigh` is initialised to the seed price and only ever increases. It is semantically distinct from `fiftyTwoWeekHigh` but will hold the same value in this simulation due to the absence of persistent price history.
- `fiftyTwoWeekHigh` is initialised to the seed price and only ever increases.
- The `PriceFeedGenerator` component is abstracted behind an interface so it can be replaced without changing the feed dispatch logic.
- Feed generation produces data for between 1 and 10 randomly selected supported tickers every 250 ms.
- The cache is never expired or evicted. Entries are only overwritten by new feed ticks.
