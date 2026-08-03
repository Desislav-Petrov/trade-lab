# Decision: Real Market Feed via Finnhub Integration

**Date:** 2026-08-03  
**Status:** accepted

## Context

Issue #44 introduces a real market data feed sourced from Finnhub's `/quote` REST endpoint. This requires several behavioural changes that contradict or supersede existing domain doc statements:

1. `market-data-feed-routing` Flow C step 3b previously stated that `feedType = REAL` falls back to synthetic data — "in a future iteration". That future iteration is now.
2. `market-data-websocket-feed` Flow B described feed generation as random 1–10 symbols every 250 ms. The real feed operates on a different cadence: round-robin, one symbol per second, driven by a Spring `@Scheduled` component. The synthetic feed retains its existing cadence independently.
3. `MarketDataSnapshot.fiftyTwoWeekHigh` semantics diverge between feed paths. On the synthetic path it remains a running max since startup. On the real feed path it is set equal to `dayHigh` (sourced from Finnhub's `h` field) because no persistent 52-week price history is available from the free Finnhub tier.
4. `MarketDataSnapshot.dayHigh` and `dayLow` semantics diverge between feed paths. On the synthetic path they are running max/min since startup. On the real feed path they are the exchange-reported day high/low from Finnhub's `h` and `l` fields, overwriting the cached value on every tick.
5. A `MarketDataFeedAdapter` interface is introduced to abstract the feed source. All feed consumers interact with this interface only. Synthetic and Finnhub are both implementations. This aligns with the existing note in `MarketDataSnapshot` Business Rules that "the `PriceFeedGenerator` component is abstracted behind an interface."

## Decision

- Introduce a `MarketDataFeedAdapter` interface in the `marketdata` domain. Synthetic and Finnhub are both concrete implementations.
- Add the Finnhub Swagger spec to `services/contract/` and generate an OpenAPI client (not server stubs) in the `marketdata` domain.
- The Finnhub API key is an environment-level Spring config value, passed via `X-Finnhub-Token` header.
- A Spring `@Scheduled` component polls Finnhub once per second, cycling symbols round-robin from the supported universe.
- Failed Finnhub calls are silently dropped — no retry, no error propagation.
- Symbol → company name enrichment is performed via an in-memory map seeded from the existing supported universe resources file before any tick is dispatched.
- On the real feed path: `dayHigh` = Finnhub `h`, `dayLow` = Finnhub `l`, `fiftyTwoWeekHigh` = Finnhub `h`, `currentPrice` = Finnhub `c`, `open` = Finnhub `o`.
- On the synthetic feed path: all existing behaviours are unchanged.
- The frontend settings toggle is updated to correctly send and reflect the `REAL` enum value. The backend already handles `UserSettingsChangedEvent` to update the feed-type routing cache — no change needed to that flow.

## Consequences

- `domain/model/market-data-snapshot.md` — Seed, Update, and Business Rules sections updated to describe real vs synthetic path divergence for `dayHigh`, `dayLow`, and `fiftyTwoWeekHigh`.
- `domain/flows/market-data-feed-routing.md` — Flow A step 4 and Flow C step 3b updated; "falls back to synthetic" caveat removed.
- `domain/flows/market-data-websocket-feed.md` — Flow B updated to clarify the two independent feed generators and their respective cadences.
- `domain/flows/finnhub-price-feed.md` — New flow doc created describing the Finnhub polling lifecycle end to end.
- The synthetic feed is not removed. Both feed paths coexist and are selected per-user at dispatch time via the existing feed-type routing cache.
