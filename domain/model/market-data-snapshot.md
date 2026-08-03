# MarketDataSnapshot

## Overview

Represents the latest known price data for a single ticker symbol, held exclusively in the backend's in-memory cache within the Market Data domain. A snapshot entry is created or overwritten each time the active price feed emits data for a symbol. It is never persisted to the database. On WebSocket connection, the backend uses this cache to push an initial snapshot to the connecting user for every ticker they are subscribed to. Subsequent feed updates overwrite the relevant entry and are pushed in real time to all connected users subscribed to that ticker.

Two feed paths exist and coexist at runtime. Which path applies to a given user is determined by the feed-type routing cache (see `domain/flows/market-data-feed-routing.md`). The snapshot cache is shared — the last writer wins per ticker, regardless of feed path.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| ticker | string | yes | 4-letter stock ticker symbol (e.g. `AAPL`). Acts as the cache key. |
| companyName | string | yes | Human-readable company name. On the synthetic path: sourced from the supported tickers configuration. On the real feed path: sourced from the in-memory symbol → company name lookup seeded from the supported universe resources file. |
| currentPrice | decimal | yes | Latest traded price in USD. 3 decimal places. |
| open | decimal | yes | **Synthetic path:** seed price set once at application startup. Never changes within a session. **Real feed path:** opening price of the day as reported by Finnhub (`o` field). Overwritten on every tick. 3 decimal places. |
| dayLow | decimal | yes | **Synthetic path:** lowest `currentPrice` recorded since application startup. Updated on every tick when the new price is lower. **Real feed path:** day low as reported by Finnhub (`l` field). Overwritten on every tick. 3 decimal places. |
| dayHigh | decimal | yes | **Synthetic path:** highest `currentPrice` recorded since application startup. Updated on every tick when the new price is higher. **Real feed path:** day high as reported by Finnhub (`h` field). Overwritten on every tick. 3 decimal places. |
| fiftyTwoWeekHigh | decimal | yes | **Synthetic path:** highest `currentPrice` recorded since application startup (same value as `dayHigh` in simulation). **Real feed path:** set equal to `dayHigh` (Finnhub `h` field) on every tick. No 52-week history is available on the free Finnhub tier. 3 decimal places. |
| updatedAt | datetime | yes | Timestamp of the last feed update that wrote to this entry. |

## Behaviors

- **Seed (synthetic path)**: At application startup, the `SyntheticPriceFeedAdapter` generates an initial `currentPrice` for every supported ticker by drawing a random fractional value uniformly between **$200.000 and $400.000** (3 decimal places). This seed price is written to `currentPrice`, `open`, `dayLow`, `dayHigh`, and `fiftyTwoWeekHigh` simultaneously. The cache is fully populated before any WebSocket connections are accepted.
- **Update (synthetic path)**: Each time the synthetic feed emits a tick, the new `currentPrice` is calculated by applying a random percentage change to the previous `currentPrice`. Direction (up or down) is chosen with equal 50/50 probability. Magnitude is drawn uniformly from **0.5%–1.5%** of the previous price, rounded to 3 decimal places. `dayLow` is updated if the new price is lower; `dayHigh` and `fiftyTwoWeekHigh` are updated if the new price is higher. `open` is never modified after seeding. `updatedAt` is set to the current timestamp.
- **Update (real feed path)**: Each time the Finnhub polling scheduler emits a tick for a ticker, the snapshot cache entry for that ticker is overwritten with values mapped from the Finnhub `/quote` response: `currentPrice` ← `c`, `open` ← `o`, `dayHigh` ← `h`, `dayLow` ← `l`, `fiftyTwoWeekHigh` ← `h`. `companyName` is set from the in-memory symbol → company name lookup. `updatedAt` is set to the current timestamp.
- **Snapshot**: When a user establishes a WebSocket connection, the backend reads all cache entries for the tickers the user is subscribed to and sends them as a single snapshot message. The snapshot is sourced from the shared in-memory cache regardless of feed path.

## Events

_No domain events are emitted directly by this entity. It is an internal cache structure. Feed updates trigger WebSocket push messages, not application events._

## Relationships

- **AssetSubscription** (`reference`): The subscription lookup is used to determine which `MarketDataSnapshot` entries are relevant for a given connected user. `MarketDataSnapshot` does not own or reference `AssetSubscription` directly — the relationship is resolved at runtime by the WebSocket feed component.

## Business Rules

- `MarketDataSnapshot` is in-memory only. It is never written to the database.
- The cache is keyed by `ticker`. There is at most one entry per ticker at any time.
- All price fields (`currentPrice`, `open`, `dayLow`, `dayHigh`, `fiftyTwoWeekHigh`) are positive decimals rounded to 3 decimal places.
- The cache is fully initialised at application startup (via the synthetic seed) before any WebSocket connections are accepted.
- The feed source is abstracted behind a `MarketDataFeedAdapter` interface. The synthetic (`SyntheticPriceFeedAdapter`) and real (`FinnhubPriceFeedAdapter`) implementations both write to the same snapshot cache. Feed consumers never reference a concrete adapter class.
- On the synthetic path: `dayLow` only ever decreases from the seed value; `dayHigh` and `fiftyTwoWeekHigh` only ever increase. `open` is immutable after seed.
- On the real feed path: `dayHigh`, `dayLow`, `open`, and `fiftyTwoWeekHigh` are all overwritten on every tick from Finnhub response fields. They are not constrained to monotonic movement.
- `fiftyTwoWeekHigh` equals `dayHigh` on the real feed path. This is an acknowledged limitation of the free Finnhub tier — no persistent 52-week history is available. See `decisions/2026-08-03-real-market-feed-finnhub.md`.
- The in-memory symbol → company name lookup is seeded from the supported universe resources file at application startup. It is never modified at runtime.
- The two feed adapters run independently. The synthetic adapter generates ticks for 1–10 random tickers every 250 ms. The Finnhub adapter polls one ticker per second in round-robin order over the supported universe.
- If a Finnhub API call fails for any reason, the tick is silently dropped. The existing cache entry for that ticker is not modified.
- The cache is never expired or evicted. Entries are only overwritten by new feed ticks.
