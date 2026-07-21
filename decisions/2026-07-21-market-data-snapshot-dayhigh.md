# Decision: Add dayHigh to MarketDataSnapshot and introduce realistic feed generation rules

**Date:** 2026-07-21  
**Status:** accepted

## Context

Issue #111 identified that the random price feed was generating unrealistic data — starting prices could be arbitrarily high and tick-to-tick movements were too steep. The fix required updating both the seeding logic and the per-tick change algorithm. During planning it was also identified that `MarketDataSnapshot` lacked a `dayHigh` field: only `dayLow` and `fiftyTwoWeekHigh` existed, leaving the intra-session high with no dedicated field.

## Decision

1. Add `dayHigh` to `MarketDataSnapshot` as a new required field tracking the highest `currentPrice` recorded since application startup.
2. Retain `fiftyTwoWeekHigh` as a semantically distinct field. In this simulation both fields track the running maximum since startup and will hold identical values, but they are kept separate for future extensibility (e.g. if persistent price history is introduced).
3. Seed each ticker's initial price by drawing uniformly at random from **$200.000–$400.000** (3 decimal places). The seed price is written to `currentPrice`, `open`, `dayLow`, `dayHigh`, and `fiftyTwoWeekHigh` simultaneously.
4. On each tick, compute the new price by applying a random ±percentage change where: direction is 50/50, magnitude is drawn uniformly from **0.5%–1.5%** of the previous price, result rounded to 3 decimal places.

## Consequences

- `MarketDataSnapshot` gains one new field (`dayHigh`). Any existing backend DTO, WebSocket payload, and frontend type that references `MarketDataSnapshot` must be updated to include `dayHigh`.
- The `PriceFeedGenerator` implementation must be updated to apply the new seeding range and tick-change algorithm.
- `market-data-snapshot.md` has been updated in place to reflect all changes.
- The OpenAPI contract for any endpoint or WebSocket message that returns snapshot data must be updated to include `dayHigh`.
