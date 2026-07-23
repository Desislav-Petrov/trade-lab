# Market Data Feed Routing

## Overview

Describes how the Market Data domain determines which type of feed (synthetic or real) to deliver to each connected user, and how it keeps that determination current as users change their settings. Feed routing is based on an in-memory cache keyed by `userId → feedType`. This cache is seeded from the database at application startup and kept current by consuming `UserSettingsChangedEvent`. No database query is made per-tick or per-connection — the cache is the sole runtime source of truth for feed routing. In this iteration, selecting `REAL` stores the preference but the backend serves synthetic data for all users because no real feed integration exists yet.

---

## Flow A — Seed Feed-Type Cache at Startup

At application startup, before any WebSocket connections are accepted, the Market Data domain loads all user feed-type preferences into its in-memory cache.

### Actors

- **System (Market Data)**: The Market Data backend performing the startup seed.

### Preconditions

- The application is starting up.
- The `user_settings` table is accessible.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Market Data) | Query all user settings | Reads all `UserSettings` rows from the `user_settings` table via the User domain `api/` interface. Retrieves `userId` and `feedType` for every row. |
| 2 | System (Market Data) | Populate feed-type cache | Writes each `userId → feedType` pair into the in-memory feed-type cache. |
| 3 | System (Market Data) | Mark cache as ready | Cache is considered fully initialised. WebSocket connections may now be accepted. |

### Postconditions

- The in-memory feed-type cache contains one entry per registered user.
- Users with no explicit preference are not expected — every user has a `UserSettings` row from registration.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Database unavailable at startup | Cannot read `user_settings` | Application startup fails. WebSocket connections are not accepted. |

---

## Flow B — Update Feed-Type Cache on Settings Change

When a user changes their feed type preference, the User domain emits `UserSettingsChangedEvent`. The Market Data domain listens and updates its cache entry for that user.

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
| 2 | System (Market Data) | Update cache entry | Overwrites the `userId → feedType` entry in the in-memory feed-type cache with the new `feedType` value from the event. |

### Postconditions

- The feed-type cache reflects the user's latest preference.
- Subsequent ticks dispatched to this user will use the updated feed type.
- Eventual consistency is acceptable — ticks in-flight at the moment of the cache update may still use the old feed type.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Unknown userId in event | `userId` not in cache (e.g. new user edge case at startup) | Cache entry is created with the supplied `feedType`. No error. |

---

## Flow C — Route Feed Per User on Tick Dispatch

During tick dispatch (Market Data WebSocket Feed Flow B), the system checks the feed-type cache to determine which data source to use for each connected user before dispatching a `TICK` message.

### Actors

- **System (Market Data)**: The price feed generator, feed-routing logic, and WebSocket dispatch component.

### Preconditions

- A WebSocket connection is established for the user (Market Data WebSocket Feed Flow A has completed).
- The feed-type cache is seeded (Flow A of this document has completed).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Market Data) | Generate tick | Price feed generator produces new price values (synthetic in this iteration). |
| 2 | System (Market Data) | Resolve feed type for user | For each connected user who is subscribed to an affected ticker, looks up the user's `feedType` in the in-memory feed-type cache. |
| 3a | System (Market Data) | Dispatch synthetic tick | If `feedType` is `SYNTHETIC` (or cache entry is absent): uses the synthetically generated price values to build the `TICK` message and dispatches it to the user's WebSocket connection. |
| 3b | System (Market Data) | Dispatch real tick _(future)_ | If `feedType` is `REAL`: in this iteration, falls back to synthetic data (no real feed source exists). In a future iteration, reads from a real market data source instead. |

### Postconditions

- Each connected user receives tick data sourced according to their feed-type preference (both resolve to synthetic in this iteration).
- The feed type check adds no perceptible latency to tick dispatch.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Cache miss for connected user | `userId` not in feed-type cache during dispatch | Falls back to `SYNTHETIC`. Logs a warning. |

---

## Flow D — Apply Feed Type at Connection Time

When a user opens a WebSocket connection, the backend reads their feed type from the cache to determine which data source to use for the initial snapshot and all subsequent ticks on this connection.

### Actors

- **System (Market Data)**: WebSocket connection handler.

### Preconditions

- A WebSocket connection request arrives from the frontend.
- Feed-type cache is seeded (Flow A has completed).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Market Data) | Accept connection | WebSocket handshake completes. `userId` is read from the query parameter. |
| 2 | System (Market Data) | Read feed type from cache | Looks up `userId` in the feed-type cache. Defaults to `SYNTHETIC` if no entry is found. |
| 3 | System (Market Data) | Build and dispatch snapshot | Uses the feed type to determine the data source for the snapshot. In this iteration both `SYNTHETIC` and `REAL` use synthetic data. Dispatches the `SNAPSHOT` message. |

### Postconditions

- The connection is associated with the correct feed type for this user.
- The snapshot is built from the appropriate data source.

---

## Events Consumed

| Event | Source Domain | Trigger |
|-------|--------------|---------|
| `UserSettingsChangedEvent` | User | Emitted after a successful settings PATCH |

## Domain Models Involved

- **UserSettings**: Read in bulk at startup (Flow A). Not read per-request thereafter.
- **MarketDataSnapshot**: Used as the data source for synthetic feed ticks and snapshots.
- **Session**: `userId` is taken from the WebSocket query parameter and used as the cache key for feed-type lookup.
