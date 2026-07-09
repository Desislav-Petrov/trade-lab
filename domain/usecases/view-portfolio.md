# Use Case: View Portfolio

## Goal

An authenticated user navigates to the Portfolio page, selects a trading account, and views their current stock holdings and cash balance — enriched with live prices, computed P&L, and portfolio allocation percentages — in a sortable table.

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
- `domain/model/session`

## Flows

- `domain/flows/view-portfolio` (Flows A, B, C, D)
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

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| Account fetch fails | Selector shows error: "Could not load accounts." Table not rendered. |
| No active accounts | Selector shows empty state: "No accounts available. Open an account first." |
| Holdings fetch fails (price data unavailable) | Page shows: "Could not load portfolio. Price data unavailable." |
| Holdings fetch fails (balance data unavailable) | Page shows: "Could not load portfolio. Balance data unavailable." |
| Account not found (HTTP 404) | Page shows: "Account not found." |
| Account not owned by user (HTTP 403) | Page shows generic error message. |
| No stock positions | Table renders cash row only. No error. |
| Total portfolio value is zero | All `% of Portfolio` values rendered as `—`. |
| Unauthenticated request (HTTP 401) | Frontend redirects to `/login`. |

## Out of Scope

- Selling stock from the Portfolio page (separate future use case).
- Historical portfolio value charts or snapshots.
- Portfolio filtering by asset type or date range.
- Real-time price streaming on the Portfolio page — prices are fetched on demand at page load and account switch, not via WebSocket.
- FX conversion — all values are in the account's base currency only.
- Risk metrics (concentration, volatility, Sharpe ratio).
- Crypto or other non-stock asset types.
