# Use Case: View Portfolio and Sell Stock

## Goal

An authenticated user navigates to the Portfolio page, selects a trading account, views their current stock holdings and cash balance — enriched with live prices, computed P&L, and portfolio allocation percentages — and can explore portfolio insights via charts, all from a tabbed interface. The user can also sell any stock holding directly from the Holdings tab, and explore their full trade price history in the Advanced Insights tab.

## Actor

Authenticated User — a logged-in user navigating to `/portfolio`.

## Screen

- **Route:** `/portfolio`
- **Page:** `PortfolioPage`
- **Entry point:** User clicks "Portfolio" in the sidebar.
- **Tabs:** Insights (default) | Holdings | Advanced Insights

## Trigger

User navigates to `/portfolio`.

## Domain Models

- `domain/model/position`
- `domain/model/position-fill`
- `domain/model/account`
- `domain/model/market-data-snapshot`
- `domain/model/order`
- `domain/model/session`

## Flows

- `domain/flows/view-portfolio` (Flows A, B, C, D, E, F)
- `domain/flows/view-portfolio-insights` (Flows A, B)
- `domain/flows/view-advanced-insights` (Flows A, B, C)
- `domain/flows/sell-stock` (Flows A, B, C, D)
- `domain/flows/aggregate-stock-position` (Flow A — background, no user interaction)

## Happy Path

1. User navigates to `/portfolio`.
2. Frontend fetches the user's active accounts and populates the page-level account selector above the tab bar. The first account is selected by default if no prior selection exists in the `portfolio` Zustand slice.
3. The Insights tab is active by default. Frontend calls `GET /api/v1/portfolio/holdings?accountId={accountId}`.
4. Portfolio backend loads all `Position` rows with `quantity > 0` for the account, calls the Market Data `api/` in bulk to retrieve current prices, calls the Ledger `api/` for the cash balance, and computes both holdings derived fields and insight aggregations.
5. Backend returns a single extended response: `holdings` array, `cash` object, and `insights` object.
6. Frontend renders the Insights tab with three charts from the `insights` object:
   - **Chart 1** — Asset class pie chart: Cash vs. Stock as % of total portfolio value.
   - **Chart 2** — Stock holdings pie chart: each stock as % of total stock-only value (cash excluded from denominator).
   - **Chart 3** — Unrealised P&L diverging bar chart: each stock's absolute unrealised P&L (positive bars above axis, negative bars below).
7. User clicks the **Holdings** tab. Frontend renders the holdings table from the cached response — no new API call. One row per stock holding, one pinned cash row at the bottom. Default sort is ticker ascending.
8. User clicks a column header to re-sort the table client-side. No new API call is made.
9. User clicks the **Advanced Insights** tab. Frontend calls `GET /api/v1/portfolio/fills?accountId={accountId}`.
10. Portfolio backend queries all `PositionFill` rows for the account scoped to the authenticated user, grouped by ticker and ordered by `filledAt` ascending.
11. Frontend renders a step-line price history chart. One step line per symbol; time on the x-axis, execution price on the y-axis. Each fill is a dot: green for BUY, red for SELL. All symbols are visible by default.
12. User clicks a symbol in the chart legend to toggle its visibility on or off. No API call is made.
13. User selects a different account from the page-level account selector. Frontend re-fetches holdings and fill history for the new account; all tabs reflect the new account.
14. User right-clicks a stock row on the Holdings tab and selects "Sell" (`view-portfolio` Flow F). The frontend fetches the indicative price from the backend and opens the sell panel (`sell-stock` Flow A).
15. User enters a sell quantity (≤ holding quantity); the panel displays real-time estimated proceeds (`sell-stock` Flow B).
16. User clicks "Confirm". The order is submitted; the backend fills it at `executionPrice` and the panel shows the fill confirmation with actual proceeds (`sell-stock` Flow C). The holdings table and account balance are refreshed.
17. User clicks "Decline" at any point to close the panel without placing an order (`sell-stock` Flow D).

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| Account fetch fails | Selector shows error: "Could not load accounts." Tab content not rendered. |
| No active accounts | Selector shows empty state: "No accounts available. Open an account first." |
| Holdings fetch fails (price data unavailable) | Page shows: "Could not load portfolio. Price data unavailable." |
| Holdings fetch fails (balance data unavailable) | Page shows: "Could not load portfolio. Balance data unavailable." |
| Account not found (HTTP 404) | Page shows: "Account not found." |
| Account not owned by user (HTTP 403) | Page shows generic error message. |
| No stock positions | Holdings tab renders cash row only. Insights Charts 2 and 3 show empty state. No error. |
| Total portfolio value is zero | All `% of Portfolio` values rendered as `—`. All insight charts show empty state. |
| Fill history fetch fails | Advanced Insights tab shows: "Could not load price history. Please try again." No chart rendered. |
| No fills exist for account | Advanced Insights tab shows empty state: "No trade history to display." |
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
- Cross-account aggregation — all insights are scoped to a single selected account.
