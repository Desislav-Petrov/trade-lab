# Decision: WebSocket transport for real-time market data feed

**Date:** 2026-06-25  
**Status:** accepted

## Context

The Stock Trading page needs to display a live-updating grid of price data for every ticker a user is subscribed to. Price ticks are generated every 250 ms. Polling via REST would introduce unnecessary latency and backend load at that frequency. A push-based transport is required.

The platform currently has no real authentication — login is email selection with no token or credential issued. The WebSocket connection needs to identify the user to scope the pushed feed to their subscriptions only.

The backend needs to know which users are subscribed to which tickers at the point of every tick dispatch, and must keep that mapping current as subscriptions change while a user is connected.

## Decision

1. **WebSocket transport**: A persistent WebSocket endpoint is added to the Market Data backend at `ws://.../api/v1/market-data/feed`. This runs alongside the existing REST APIs — no REST endpoints are replaced.

2. **User identification via query parameter**: The `userId` is passed as a query parameter on the WebSocket URL (`?userId={userId}`). This is a temporary measure consistent with the platform's current no-auth posture. When real authentication is introduced, the `userId` parameter will be replaced by a bearer token, and the backend will extract the user identity from the token on handshake.

3. **In-memory snapshot cache (`MarketDataSnapshot`)**: The backend maintains an in-memory cache of the latest price data per ticker. This cache is seeded at startup and updated on every tick. It is never persisted to the database. The snapshot cache is the sole source of truth for the initial snapshot pushed on WebSocket connect and for the immediate tick pushed when a new subscription is added mid-session.

4. **In-memory subscription lookup**: The WebSocket feed component loads all `AssetSubscription` records at startup and builds two indexes: ticker → set of connected userIds, and userId → set of tickers. This lookup is kept current by listening to `AssetSubscribedEvent` and `AssetUnsubscribedEvent` on the Spring application event bus. No database query is made on each tick dispatch.

5. **Abstracted price feed generator**: Random price generation is encapsulated in a `PriceFeedGenerator` interface with a single random implementation. The feed dispatcher depends on the interface, not the implementation. This allows the random generator to be swapped for a real market data source without touching dispatch logic.

6. **Grid row removal on unsubscribe**: The frontend removes the grid row immediately on REST unsubscribe success (HTTP 204), rather than waiting for the WebSocket feed to stop. This gives instant feedback. The backend then stops dispatching ticks for that ticker asynchronously via the event bus.

## Consequences

- A new `MarketDataSnapshot` entity is introduced. It is in-memory only and has no database representation.
- `AssetSubscription` entity and `manage-asset-subscriptions` flow docs are updated to note that both events are consumed by the WebSocket feed component.
- A new `market-data-websocket-feed` flow doc is added covering the full WebSocket lifecycle (connect, snapshot, tick, subscription change mid-session, disconnect).
- A new `trade-stock-page` use case doc is added covering the full multi-flow user journey on `/trade`.
- When real authentication is introduced, the `userId` query parameter approach must be revisited and this decision updated.
- The `PriceFeedGenerator` interface must be kept as the abstraction boundary; the random implementation must never be referenced directly outside of Spring configuration/wiring.
