# Market Data WebSocket Feed

## Overview

Covers the full lifecycle of the real-time market data WebSocket connection between the frontend and the backend Market Data service. When a user opens the Stock Trading page, the frontend establishes a persistent WebSocket connection identified by `userId`. The backend immediately pushes a snapshot of the latest cached price data for all of the user's subscribed tickers. It then continues to push live updates as the price feed generator emits ticks. The connection is kept open for the duration of the user's visit to the page and torn down on navigation away. Subscription changes made mid-session (via the REST subscription flows) are reflected in the live feed automatically without reconnection.

Feed type routing (synthetic vs real) is determined per user from the in-memory feed-type cache at connection time and on every tick dispatch. See `domain/flows/market-data-feed-routing.md` for the full feed-routing lifecycle.

---

## Flow A — Establish Connection and Receive Snapshot

The frontend opens a WebSocket connection when the Stock Trading page mounts and receives an immediate snapshot of current prices for all subscribed tickers.

### Actors
- **Authenticated User**: A logged-in user who has navigated to `/trade`.
- **Guest Browser**: The React frontend managing the WebSocket connection and rendering the market data grid.
- **System**: The Market Data backend handling the WebSocket handshake, snapshot dispatch, and live feed.

### Preconditions
- The user has an active session (is logged in).
- The user's subscriptions have been loaded (Flow A of `manage-asset-subscriptions`).
- The backend in-memory `MarketDataSnapshot` cache is fully seeded.
- The backend in-memory feed-type cache is fully seeded (see `market-data-feed-routing` Flow A).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest Browser | Open WebSocket connection | On mount of the Stock Trading page, opens a WebSocket connection to `ws://.../api/v1/market-data/feed?userId={userId}`. |
| 2 | System | Authenticate connection | Reads `userId` from the query parameter. Looks up the user's subscription list from the in-memory subscription lookup. Rejects with close code `4401` if `userId` is missing or does not resolve to a known user. |
| 3 | System | Read feed type | Looks up the user's `feedType` from the in-memory feed-type cache. Defaults to `SYNTHETIC` if no entry is found. |
| 4 | System | Build snapshot | Reads the `MarketDataSnapshot` cache entries for every ticker the user is subscribed to. Uses the feed type determined at step 3 to select the data source (both resolve to synthetic in this iteration). |
| 5 | System | Push snapshot message | Sends a single WebSocket message of type `SNAPSHOT` containing an array of `MarketDataUpdate` items — one per subscribed ticker — to the connected client. |
| 6 | Guest Browser | Render grid | Receives the `SNAPSHOT` message. Populates the market data grid with one row per ticker. Each row displays: `ticker`, `companyName`, `currentPrice`, `open`, `dayLow`, `fiftyTwoWeekHigh`. |

### Postconditions
- A WebSocket session exists on the backend, keyed by `userId`.
- The market data grid is populated with the latest cached price data for all subscribed tickers.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Missing userId | `userId` query param absent | Backend closes connection with code `4401`. Frontend shows an error banner on the grid. |
| Unknown userId | `userId` does not resolve to a known user | Backend closes connection with code `4401`. Frontend shows an error banner on the grid. |
| User has no subscriptions | Subscription list is empty | Snapshot message contains an empty array. Grid renders empty state. |
| Backend error during snapshot | Cache read fails | Backend closes connection with code `4500`. Frontend shows a generic error banner. |

---

## Flow B — Receive Live Price Update

The backend pushes a `TICK` message to the connected client each time the price feed generator emits data for a ticker the user is subscribed to. Before dispatching, the backend checks the user's feed type from the in-memory feed-type cache to select the correct data source.

### Actors
- **System**: The price feed generator and WebSocket dispatch component.
- **Guest Browser**: The React frontend receiving and applying updates to the grid.

### Preconditions
- A WebSocket connection is established (Flow A has completed).
- The price feed generator is running and emits ticks every 250 ms for 1–10 randomly selected tickers.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System | Generate tick | Price feed generator selects between 1 and 10 tickers at random and produces new randomised price values for each. |
| 2 | System | Update snapshot cache | Overwrites the `MarketDataSnapshot` cache entry for each affected ticker with the new values and updates `updatedAt`. |
| 3 | System | Resolve subscribers | For each affected ticker, looks up the set of `userId` values subscribed to that ticker from the in-memory subscription lookup. |
| 4 | System | Check feed type per user | For each connected subscriber, reads the user's `feedType` from the in-memory feed-type cache. In this iteration both `SYNTHETIC` and `REAL` use synthetic data. See `market-data-feed-routing` Flow C. |
| 5 | System | Dispatch TICK messages | For each connected `userId` that is subscribed to an affected ticker, sends a WebSocket message of type `TICK` containing the updated `MarketDataUpdate` item for that ticker. A single dispatch cycle may send multiple `TICK` messages to the same connection if the user is subscribed to more than one of the affected tickers. |
| 6 | Guest Browser | Update grid row | Receives each `TICK` message. Finds the grid row matching the `ticker` field and updates the price columns in place without re-rendering the entire grid. |

### Postconditions
- The `MarketDataSnapshot` cache reflects the latest generated values.
- The grid row for each affected ticker shows updated price data within one tick cycle (≤ 250 ms latency from generation to display).

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| User not connected | A tick arrives for a ticker whose subscriber is not currently connected | Tick is silently discarded for that user. No error. |
| Stale WebSocket session | Backend attempts to send to a closed session | Session is removed from the active connections map. No error propagated. |
| Feed type cache miss on dispatch | `userId` not found in feed-type cache | Falls back to `SYNTHETIC`. Logs a warning. |

---

## Flow C — Subscription Added Mid-Session

A user adds a new ticker subscription via the REST API while their WebSocket connection is open. The feed automatically begins pushing updates for the new ticker without reconnection.

### Actors
- **System**: The Market Data backend receiving the `AssetSubscribedEvent` on the Spring application event bus.
- **Guest Browser**: The React frontend receiving the first tick for the new ticker.

### Preconditions
- A WebSocket connection is established (Flow A has completed).
- The user has successfully added a new subscription (Flow B of `manage-asset-subscriptions`).
- `AssetSubscribedEvent` has been emitted with the new ticker(s).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System | Receive AssetSubscribedEvent | The WebSocket feed component listens on the Spring application event bus and receives the event. |
| 2 | System | Update subscription lookup | Adds the new ticker(s) to the in-memory subscription lookup for the given `userId`. |
| 3 | System | Push snapshot for new tickers | Reads the current `MarketDataSnapshot` cache entries for the newly added tickers and immediately sends a `TICK` message to the user's open WebSocket connection for each new ticker. |
| 4 | Guest Browser | Append new grid row(s) | Receives the `TICK` message(s). Adds a new row to the market data grid for each new ticker. |

### Postconditions
- The in-memory subscription lookup includes the new ticker(s) for the user.
- New rows appear in the market data grid populated with current cached prices.
- Subsequent ticks for the new ticker(s) will be dispatched to this user.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| User not currently connected | `AssetSubscribedEvent` fires but the user has no open WebSocket session | Lookup is updated. No push is attempted. Grid will reflect the subscription on next connection. |

---

## Flow D — Subscription Removed Mid-Session

A user removes a ticker subscription via the REST API while their WebSocket connection is open. The feed stops pushing updates for the removed ticker. The grid row is removed immediately on the frontend.

### Actors
- **System**: The Market Data backend receiving the `AssetUnsubscribedEvent` on the Spring application event bus.
- **Guest Browser**: The React frontend removing the grid row on successful unsubscribe REST response.

### Preconditions
- A WebSocket connection is established (Flow A has completed).
- The user has successfully removed a subscription (Flow C of `manage-asset-subscriptions`).
- `AssetUnsubscribedEvent` has been emitted with the removed ticker(s).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest Browser | Remove grid row(s) | On receiving the HTTP 204 success response from the unsubscribe REST call, immediately removes the row(s) for the unsubscribed ticker(s) from the market data grid. |
| 2 | System | Receive AssetUnsubscribedEvent | The WebSocket feed component listens on the Spring application event bus and receives the event. |
| 3 | System | Update subscription lookup | Removes the ticker(s) from the in-memory subscription lookup for the given `userId`. |

### Postconditions
- The grid row for the removed ticker is no longer visible.
- The in-memory subscription lookup no longer maps the removed ticker(s) to this user.
- No further `TICK` messages are dispatched to this user for the removed ticker(s).

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| User not currently connected | `AssetUnsubscribedEvent` fires but the user has no open WebSocket session | Lookup is updated silently. No action needed. |

---

## Flow E — Disconnect

The WebSocket connection is torn down when the user navigates away from the Stock Trading page or closes the browser tab.

### Actors
- **Guest Browser**: The React frontend closing the WebSocket on component unmount.
- **System**: The Market Data backend handling the disconnection.

### Preconditions
- A WebSocket connection is established.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest Browser | Unmount Stock Trading page | User navigates away or closes the tab. The React component cleanup closes the WebSocket connection. |
| 2 | System | Handle disconnection | Removes the WebSocket session from the active connections map for this `userId`. |

### Postconditions
- No active WebSocket session exists for the user.
- No further messages are dispatched to the disconnected client.

---

## WebSocket Message Types

| Type | Direction | Description |
|------|-----------|-------------|
| `SNAPSHOT` | Server → Client | Sent once on connect. Contains an array of `MarketDataUpdate` items for all subscribed tickers. |
| `TICK` | Server → Client | Sent on each feed update. Contains one `MarketDataUpdate` item for a single ticker. Also sent immediately when a new subscription is added mid-session. |

### MarketDataUpdate payload

| Field | Type | Description |
|-------|------|-------------|
| ticker | string | 4-letter ticker symbol |
| companyName | string | Human-readable company name |
| currentPrice | number | Latest price in USD, 3 decimal places |
| open | number | Opening price in USD, 3 decimal places |
| dayLow | number | Day low in USD, 3 decimal places |
| fiftyTwoWeekHigh | number | 52-week high in USD, 3 decimal places |

---

## Domain Models Involved

- **MarketDataSnapshot**: Read in Flows A, B, and C to build snapshot and tick payloads; written in Flow B when the feed generator emits a tick.
- **AssetSubscription**: The in-memory subscription lookup (keyed by ticker → list of userIds, and by userId → list of tickers) is built from `AssetSubscription` records at startup and kept current via `AssetSubscribedEvent` and `AssetUnsubscribedEvent`.
- **UserSettings**: The in-memory feed-type cache (keyed by userId → feedType) is seeded from `UserSettings` rows at startup and kept current via `UserSettingsChangedEvent`. Consulted at connection time (Flow A step 3) and on every tick dispatch (Flow B step 4).
- **Session**: `userId` is taken from the WebSocket query parameter and must match a known user.
