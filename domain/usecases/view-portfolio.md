# Use Case: View Portfolio and Sell Stock

## Goal

An authenticated user navigates to the Portfolio page, selects a trading account, views their current stock holdings and cash balance — enriched with live prices, computed P&L, and portfolio allocation percentages — in a sortable table, and can sell any stock holding directly from the table.

## Actor

Authenticated User — a logged-in user navigating to `/portfolio`.

## Screen

- **Route:** `/portfolio`
- **Page:** `PortfolioPage`
- **Entry point:** User clicks "Portfolio" in the sidebar.

## Trigger

User navigates to `/portfolio`.

## Domain Models

- `domain/model/position`
- `domain/model/account`
- `domain/model/market-data-snapshot`
- `domain/model/order`
- `domain/model/session`

## Flows

- `domain/flows/view-portfolio` (Flows A, B, C, D, E)
- `domain/flows/sell-stock` (Flows A, B, C, D)
- `domain/flows/aggregate-stock-position` (Flow A — background, no user interaction)

## Happy Path

1. User navigates to `/portfolio`.
2. Frontend fetches the user's active accounts and populates the account selector dropdown. The first account is selected by default if no prior selection exists in the `portfolio` Zustand slice.
3. Frontend calls `GET /api/v1/portfolio/holdings?accountId={accountId}`.
4. Portfolio backend loads all `Position` rows with `quantity > 0` for the account, calls the Market Data `api/` in bulk to retrieve current prices, and calls the Ledger `api/` for the cash balance.
5. Backend computes `currentValue`, `unrealisedPnL`, and `portfolioPercent` for each stock position and for the cash row, then returns the enriched response.
6. Frontend renders the holdings table: one row per stock holding, one pinned cash row at the bottom. Default sort is ticker ascending.
7. User clicks a column header to re-sort the table client-side. No new API call is made.
8. User selects a different account from the dropdown. Frontend re-fetches holdings for the new account and re-renders the table.
9. User right-clicks a stock row and selects "Sell" (`view-portfolio` Flow E). The frontend fetches the indicative price from the backend and opens the sell panel (`sell-stock` Flow A).
10. User enters a sell quantity (≤ holding quantity); the panel displays real-time estimated proceeds (`sell-stock` Flow B).
11. User clicks "Confirm". The order is submitted; the backend fills it at `executionPrice` and the panel shows the fill confirmation with actual proceeds (`sell-stock` Flow C). The holdings table and account balance are refreshed.
12. User clicks "Decline" at any point to close the panel without placing an order (`sell-stock` Flow D).

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| Account fetch fails | Selector shows error: "Could not load accounts." Table not rendered. |
| No active accounts | Selector shows empty state: "No accounts available. Open an account first." |
| Holdings fetch fails (price data unavailable) | Page shows: "Could not load portfolio. Price data unavailable." |
| Holdings fetch fails (balance data unavailable) | Page shows: "Could not load portfolio. Balance data unavailable." |
| Account not found (HTTP 404) | Page shows: "Account not found." |
| Account not owned by user (HTTP 403) | Page shows generic error message. |
| No stock positions | Table renders cash row only. No error. Sell option not available. |
| Total portfolio value is zero | All `% of Portfolio` values rendered as `—`. |
| Indicative price fetch fails | Sell panel shows error: "Could not load price. Please try again." Panel does not open. |
| Sell: quantity exceeds holding | Backend returns HTTP 200 with `status: REJECTED`. Panel shows: "Order rejected: Quantity exceeds holding." Order persisted as REJECTED. No ledger entries written. |
| Sell: duplicate idempotency key | Backend returns HTTP 409. Panel shows generic error. A new `idempotencyKey` is generated; user may retry. |
| Sell: server error | Backend returns HTTP 500. Panel shows generic error message. |
| Unauthenticated request (HTTP 401) | Frontend redirects to `/login`. |

## Out of Scope

- Selling cash (no FX trading).
- Partial fills — sell orders fill immediately and fully.
- Historical portfolio value charts or snapshots.
- Portfolio filtering by asset type or date range.
- Real-time price streaming on the Portfolio page — prices are fetched on demand at page load, account switch, and sell panel open.
- FX conversion — all values are in the account's base currency only.
- Risk metrics (concentration, volatility, Sharpe ratio).
- Crypto or other non-stock asset types.
