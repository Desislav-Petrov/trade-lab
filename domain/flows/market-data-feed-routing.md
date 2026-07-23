# Market Data Feed Routing

## Overview

Describes how the Market Data domain determines which type of feed (synthetic or real) to deliver to each connected user, and how it keeps that determination current as users change their settings. Feed routing is based on an in-memory cache keyed by `userId → feedType`. The cache is populated **lazily** — an entry is loaded from the User domain `api/` interface on first WebSocket connection for a given user, not at application startup. Subsequent lookups hit the cache directly. The cache is kept current by consuming `UserSettingsChangedEvent`. No database query is made per-tick. In this iteration, selecting `REAL` stores the preference but the backend serves synthetic data for all users because no real feed integration exists yet.

---

## Flow A — Resolve Feed Type at Connection Time (lazy load)

When a user opens a WebSocket connection, the backend checks the in-memory cache for that user's feed type. If no entry exists, it fetches the value from the User domain `api/` interface and populates the cache before proceeding.

### Actors

- **System (Market Data)**: WebSocket connection handler.

### Preconditions

- A WebSocket connection request arrives from the frontend.
- `userId` has been validated.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Market Data) | Accept connection | WebSocket handshake completes. `userId` is read from the query parameter. |
| 2 | System (Market Data) | Check cache | Looks up `userId` in the in-memory feed-type cache. |
| 3a | System (Market Data) | Cache hit — use cached value | If an entry exists for `userId`, use `feedTypeCache[userId]` as the feed type. Continue to step 4. |
| 3b | System (Market Data) | Cache miss — lazy load | If no entry exists, calls `userSettingsApi.getUserSettings(userId)` (User domain `api/` interface). Writes the returned `feedType` into `feedTypeCache[userId]`. If the User domain returns no settings (unexpected), defaults to `SYNTHETIC` and logs a WARN. |
| 4 | System (Market Data) | Build and dispatch snapshot | Uses the resolved feed type to determine the data source for the snapshot. In this iteration both `SYNTHETIC` and `REAL` use synthetic data. Dispatches the `SNAPSHOT` message. |

### Postconditions

- `feedTypeCache[userId]` is populated.
- The snapshot is built from the appropriate data source.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| User settings not found | `userSettingsApi.getUserSettings(userId)` returns null | Defaults to `SYNTHETIC`. Logs a WARN. Cache entry is written with `SYNTHETIC`. |

---

## Flow B — Update Feed-Type Cache on Settings Change

When a user changes their feed type preference, the User domain emits `UserSettingsChangedEvent`. The Market Data domain listens and updates (or creates) the cache entry for that user.

### Actors

- **System (User domain)**: Emits `UserSettingsChangedEvent` after a successful settings PATCH.
- **System (Market Data)**: Listener that updates the in-memory feed-type cache.

### Preconditions

- `UserSettingsChangedEvent` has been published on the Spring application event bus.
- The event payload contains: `userId`, `feedType`, `updatedAt`.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Market Data) | Receive UserSettingsChangedEvent | The Market Data feed-routing listener receives the event via `@EventListener`. |
| 2 | System (Market Data) | Update cache entry | Overwrites (or creates) the `userId → feedType` entry in the in-memory feed-type cache with the new `feedType` value from the event. |

### Postconditions

- The feed-type cache reflects the user's latest preference.
- Subsequent ticks dispatched to this user will use the updated feed type.
- Eventual consistency is acceptable — ticks in-flight at the moment of the cache update may still use the old feed type.

---

## Flow C — Route Feed Per User on Tick Dispatch

During tick dispatch (Market Data WebSocket Feed Flow B), the system checks the feed-type cache to determine which data source to use for each connected user before dispatching a `TICK` message.

### Actors

- **System (Market Data)**: The price feed generator, feed-routing logic, and WebSocket dispatch component.

### Preconditions

- A WebSocket connection is established for the user.
- The cache entry for the user is populated (populated lazily at connection time — Flow A).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Market Data) | Generate tick | Price feed generator produces new price values (synthetic in this iteration). |
| 2 | System (Market Data) | Resolve feed type for user | For each connected user subscribed to an affected ticker, looks up `feedType` in the in-memory feed-type cache. Defaults to `SYNTHETIC` on cache miss; logs a WARN. |
| 3a | System (Market Data) | Dispatch synthetic tick | If `feedType` is `SYNTHETIC` (or cache miss): uses synthetically generated price values to build the `TICK` message and dispatches it. |
| 3b | System (Market Data) | Dispatch real tick _(future)_ | If `feedType` is `REAL`: in this iteration, falls back to synthetic data. In a future iteration, reads from a real market data source. |

### Postconditions

- Each connected user receives tick data sourced according to their feed-type preference (both resolve to synthetic in this iteration).

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Cache miss on dispatch | `userId` not in feed-type cache during tick | Falls back to `SYNTHETIC`. Logs a WARN. |

---

## Events Consumed

| Event | Source Domain | Trigger |
|-------|--------------|---------|
| `UserSettingsChangedEvent` | User | Emitted after a successful settings PATCH |

## Domain Models Involved

- **UserSettings**: Fetched lazily at first WebSocket connection per user via `UserSettingsApi`. Not read per-tick.
- **MarketDataSnapshot**: Used as the data source for synthetic feed ticks and snapshots.
- **Session**: `userId` is taken from the WebSocket query parameter and used as the cache key for feed-type lookup.
