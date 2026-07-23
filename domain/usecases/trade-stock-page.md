# Use Case: Trade Stock Page

## Goal

An authenticated user lands on the Stock Trading page, selects a funding account, sees their current ticker subscriptions, manages that list, watches live price data update in real time in the market data grid, and can buy stock at market price — all without leaving the page.

## Actor

Authenticated User — a logged-in user navigating to `/trade`.

## Screen

- **Route:** `/trade`
- **Page:** `TradeStockPage`
- **Entry point:** User clicks "Stock Trading" in the sidebar, or is redirected here after login.

## Trigger

User navigates to `/trade`.

## Domain Models

- `domain/model/account`
- `domain/model/asset-subscription`
- `domain/model/market-data-snapshot`
- `domain/model/order`
- `domain/model/session`

## Flows

- `domain/flows/select-trading-account` (Flows A, B)
- `domain/flows/manage-asset-subscriptions` (Flows A, B, C, D)
- `domain/flows/market-data-websocket-feed` (Flows A, B, C, D, E)
- `domain/flows/buy-stock` (Flows A, B, C, D)

## Happy Path

1. User navigates to `/trade`.
2. Frontend fetches the user's active accounts (`select-trading-account` Flow A), supported tickers (`manage-asset-subscriptions` Flow D), and the user's current subscriptions (`manage-asset-subscriptions` Flow A) in parallel.
3. Frontend applies the default account selection: if no account is already stored in the `stocktrading` Zustand slice, the first account in the returned list is selected (`select-trading-account` Flow A, step 4). If no active accounts exist, the selector renders an empty state.
4. Frontend opens a WebSocket connection to the market data feed (`market-data-websocket-feed` Flow A).
5. Backend pushes a snapshot of current prices for all subscribed tickers. Frontend populates the market data grid — one row per subscribed ticker showing `ticker`, `companyName`, `currentPrice`, `open`, `dayLow`, `fiftyTwoWeekHigh`.
6. Backend continues to push live `TICK` updates every ≤ 250 ms. Each received tick updates the corresponding grid row in place.
7. User selects a different funding account via the account selector (`select-trading-account` Flow B). Selection is stored in the `stocktrading` Zustand slice. No other page behaviour changes.
8. User adds new ticker(s) via the subscription panel (`manage-asset-subscriptions` Flow B). Backend receives `AssetSubscribedEvent`, updates its subscription lookup, and immediately pushes a tick for the new ticker(s) (`market-data-websocket-feed` Flow C). New row(s) appear in the grid.
9. User removes ticker(s) via the subscription panel (`manage-asset-subscriptions` Flow C). On REST success the grid row(s) are removed immediately. Backend receives `AssetUnsubscribedEvent` and stops pushing ticks for those ticker(s) (`market-data-websocket-feed` Flow D).
10. User right-clicks a ticker row in the grid and selects "Buy" (`buy-stock` Flow A). The buy panel opens with the ticker's current price captured as `priceSnapshot` and a fresh `idempotencyKey` generated.
11. User enters a quantity; the panel displays a real-time estimated cost (`buy-stock` Flow B).
12. User clicks "Confirm". The order is submitted, the backend fills it at `executionPrice`, and the panel shows the fill confirmation with actual cost (`buy-stock` Flow C).
13. User clicks "Decline" at any point to close the panel without placing an order (`buy-stock` Flow D).
14. User navigates away. Frontend tears down the WebSocket connection (`market-data-websocket-feed` Flow E).

## Account Selector Specification

- Displays a dropdown of the user's active accounts. Each option shows the account `name` and `currency`.
- Accounts are ordered by `createdAt` ascending.
- The selected account's `id` is stored in the `stocktrading` Zustand slice and persists for the duration of the browser session.
- On every page mount the account list is re-fetched from the API. The stored selection is preserved across navigations; the default (first account) is applied only when no selection exists.
- If no active accounts exist, the selector shows: "No accounts available. Open an account first."

## Grid Specification

- Columns: `ticker` (4-char string), `companyName` (string), `currentPrice` (USD, 3 d.p.), `open` (USD, 3 d.p.), `dayLow` (USD, 3 d.p.), `fiftyTwoWeekHigh` (USD, 3 d.p.).
- All columns support ascending/descending sort. Clicking a column header cycles through ascending → descending → unsorted.
- The grid supports both horizontal and vertical scrolling.
- Each row is keyed by `ticker`. Updates are applied in-place — no row is removed and re-added on tick.
- Right-clicking any row opens a context menu with a single "Buy" option.
- If the user has no subscriptions, the grid shows an empty-state message.

## Buy Panel Specification

- Opens on right-click → "Buy" on any grid row.
- Displays: ticker symbol, company name, order type (pre-set to `MARKET`, read-only), quantity input, estimated cost (quantity × priceSnapshot, updated in real time).
- `priceSnapshot` is the `currentPrice` from the grid row at the moment of right-click. It is informational and does not affect the financial calculation.
- `executionPrice` is determined by the backend at fill time from the `MarketDataSnapshot` cache. May differ from `priceSnapshot`.
- After a successful fill, the panel shows: ticker, quantity, execution price per share, total cost (`quantity × executionPrice`), and a green tick icon with "Order filled".
- After a rejection (insufficient funds), the panel shows the rejection reason.
- Confirm button shows a green tick icon. Decline button shows a red cross icon.
- Clicking Decline at any stage closes the panel without placing an order.

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| Account fetch fails on mount | Selector shows error state: "Could not load accounts." No selection change is made. Rest of the page continues to function. |
| No active accounts | Selector renders empty state. Buy action is blocked — Confirm button disabled with message "No accounts available." |
| WebSocket connection fails on mount | Grid shows an error banner. Subscription list and account selector are still visible. User may retry by refreshing the page. |
| Snapshot message missing for a subscribed ticker | Row is not rendered until the first `TICK` arrives for that ticker. |
| Add subscription REST call fails | Error message shown in the subscription panel. No new row added. WebSocket lookup unchanged. |
| Remove subscription REST call fails | Error message shown in the subscription panel. Row remains in the grid. WebSocket lookup unchanged. |
| WebSocket drops mid-session | Frontend attempts a single reconnect. On successful reconnect, receives a fresh snapshot. If reconnect fails, grid shows a "connection lost" banner. |
| Buy: insufficient funds | Backend returns HTTP 200 with `status: REJECTED`. Panel shows: "Order rejected: Insufficient funds." Order is persisted as REJECTED. No ledger entries written. |
| Buy: duplicate idempotency key | Backend returns HTTP 409. Panel shows generic error. A new `idempotencyKey` is generated; user may retry. |
| Buy: server error | Backend returns HTTP 500. Panel shows generic error message. |
| Unauthenticated request | System returns HTTP 401. Frontend redirects to `/login`. |

## Out of Scope

- Selling stock (separate future use case).
- Fee calculations — no fees in this iteration.
- Partial fills — orders are assumed to fill fully and immediately.
- Currency matching between the selected account and a trade (deferred — no FX support yet).
- Persisting price history — the grid shows only the latest tick per ticker.
- Authentication of the WebSocket connection beyond passing `userId` as a query parameter (covered by a future auth iteration).
- Sorting persistence across sessions.
