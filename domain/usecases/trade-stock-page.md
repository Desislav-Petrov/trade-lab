# Use Case: Trade Stock Page

## Goal

An authenticated user lands on the Stock Trading page, sees their current ticker subscriptions, manages that list, and watches live price data update in real time in the market data grid — all without leaving the page.

## Actor

Authenticated User — a logged-in user navigating to `/trade`.

## Screen

- **Route:** `/trade`
- **Page:** `TradeStockPage`
- **Entry point:** User clicks "Stock Trading" in the sidebar, or is redirected here after login.

## Trigger

User navigates to `/trade`.

## Domain Models

- `domain/model/asset-subscription`
- `domain/model/market-data-snapshot`
- `domain/model/session`

## Flows

- `domain/flows/manage-asset-subscriptions` (Flows A, B, C, D)
- `domain/flows/market-data-websocket-feed` (Flows A, B, C, D, E)

## Happy Path

1. User navigates to `/trade`.
2. Frontend fetches supported tickers (`manage-asset-subscriptions` Flow D) and the user's current subscriptions (`manage-asset-subscriptions` Flow A) in parallel.
3. Frontend opens a WebSocket connection to the market data feed (`market-data-websocket-feed` Flow A).
4. Backend pushes a snapshot of current prices for all subscribed tickers. Frontend populates the market data grid — one row per subscribed ticker showing `ticker`, `companyName`, `currentPrice`, `open`, `dayLow`, `fiftyTwoWeekHigh`.
5. Backend continues to push live `TICK` updates every ≤ 250 ms. Each received tick updates the corresponding grid row in place.
6. User adds new ticker(s) via the subscription panel (`manage-asset-subscriptions` Flow B). Backend receives `AssetSubscribedEvent`, updates its subscription lookup, and immediately pushes a tick for the new ticker(s) (`market-data-websocket-feed` Flow C). New row(s) appear in the grid.
7. User removes ticker(s) via the subscription panel (`manage-asset-subscriptions` Flow C). On REST success the grid row(s) are removed immediately. Backend receives `AssetUnsubscribedEvent` and stops pushing ticks for those ticker(s) (`market-data-websocket-feed` Flow D).
8. User navigates away. Frontend tears down the WebSocket connection (`market-data-websocket-feed` Flow E).

## Grid Specification

- Columns: `ticker` (4-char string), `companyName` (string), `currentPrice` (USD, 3 d.p.), `open` (USD, 3 d.p.), `dayLow` (USD, 3 d.p.), `fiftyTwoWeekHigh` (USD, 3 d.p.).
- All columns support ascending/descending sort. Clicking a column header cycles through ascending → descending → unsorted.
- The grid supports both horizontal and vertical scrolling.
- Each row is keyed by `ticker`. Updates are applied in-place — no row is removed and re-added on tick.
- If the user has no subscriptions, the grid shows an empty-state message.

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| WebSocket connection fails on mount | Grid shows an error banner. Subscription list is still visible. User may retry by refreshing the page. |
| Snapshot message missing for a subscribed ticker | Row is not rendered until the first `TICK` arrives for that ticker. |
| Add subscription REST call fails | Error message shown in the subscription panel. No new row added. WebSocket lookup unchanged. |
| Remove subscription REST call fails | Error message shown in the subscription panel. Row remains in the grid. WebSocket lookup unchanged. |
| WebSocket drops mid-session | Frontend attempts a single reconnect. On successful reconnect, receives a fresh snapshot. If reconnect fails, grid shows a "connection lost" banner. |

## Out of Scope

- Placing actual trades from this page (separate use case).
- Persisting price history — the grid shows only the latest tick per ticker.
- Authentication of the WebSocket connection beyond passing `userId` as a query parameter (covered by a future auth iteration).
- Sorting persistence across sessions.
