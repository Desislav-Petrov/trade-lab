# View Portfolio Insights

## Overview

Covers the Insights tab on the Portfolio page (`/portfolio`). Insights is the default tab when a user navigates to `/portfolio`. It displays three charts derived from the user's current portfolio holdings for the selected account. All chart data is computed by the Portfolio backend and returned as part of the extended holdings response — the frontend performs no calculations. There is no cross-account aggregation; all charts are scoped to the currently selected account.

---

## Flow A — Load Insights Tab

The user lands on the Insights tab (the default tab at `/portfolio`). The frontend fetches the extended holdings response using the page-level selected account and renders three charts.

### Actors

- **Authenticated User**: A logged-in user navigating to `/portfolio`.
- **Guest Browser**: The React frontend rendering the Insights tab.
- **System (Portfolio)**: The Portfolio backend service computing and returning enriched holdings with insight aggregations.

### Preconditions

- The user has an active session (is logged in).
- The user's `status` is `active`.
- The page-level account selector has resolved to a default account (see `domain/flows/view-portfolio` Flow A).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Navigate to `/portfolio` | Arrives at the Portfolio page via the sidebar. The Insights tab is active by default. |
| 2 | Guest Browser | Fetch extended holdings | Calls `GET /api/v1/portfolio/holdings?accountId={accountId}`. The same endpoint used by the Holdings tab — extended to include an `insights` aggregate object. |
| 3 | System (Portfolio) | Compute insight aggregations | In addition to the standard holdings computation, the backend computes the `insights` object (see Insights Response Specification below) and includes it in the response. |
| 4 | System (Portfolio) | Return HTTP 200 | Returns the extended holdings response including `holdings`, `cash`, and `insights`. |
| 5 | Guest Browser | Render Chart 1 — Asset Class Breakdown | Renders a pie chart showing Cash vs. Stock as a percentage of total portfolio value. Uses `insights.assetClassBreakdown`. Does not render if total portfolio value is zero. |
| 6 | Guest Browser | Render Chart 2 — Stock Holdings Breakdown | Renders a pie chart showing each individual stock as a percentage of total stock-only value. Uses `insights.stockBreakdown`. Does not render if no stock positions exist. |
| 7 | Guest Browser | Render Chart 3 — Unrealised P&L Contribution | Renders a diverging bar chart showing each stock's absolute unrealised P&L (positive bars above the axis, negative bars below). Uses `insights.unrealisedPnLContribution`. Does not render if no stock positions exist. |

### Postconditions

- All three charts are rendered for the selected account.
- Charts with no data (empty stock positions, zero total value) display an appropriate empty state rather than a broken chart.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Holdings fetch fails | `GET /api/v1/portfolio/holdings` returns non-2xx | Insights tab shows: "Could not load insights. Please try again." No charts rendered. |
| No stock positions | `holdings` array is empty | Chart 2 and Chart 3 display empty state: "No stock holdings to display." Chart 1 renders with 100% Cash if cash balance > 0. |
| Total portfolio value is zero | Cash balance is zero and no stock positions | All three charts display empty state: "No portfolio data to display." |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |

---

## Flow B — Switch Account on Insights Tab

The user selects a different account from the page-level account selector while on the Insights tab. All charts refresh for the newly selected account.

### Actors

- **Authenticated User**: A logged-in user changing the selected account.
- **Guest Browser**: The React frontend re-fetching holdings for the new account.

### Preconditions

- The Portfolio page is loaded and the Insights tab is active.
- The user has more than one active account.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Select a different account | Chooses a new account from the page-level account selector dropdown. |
| 2 | Guest Browser | Store selection | Updates the selected `accountId` in the `portfolio` Zustand slice. |
| 3 | Guest Browser | Re-fetch holdings | Invalidates the TanStack Query cache for `GET /api/v1/portfolio/holdings` and re-fetches using the new `accountId`. Shows a loading state on all three charts while the fetch is in progress. |
| 4 | Guest Browser | Re-render charts | Replaces all three charts with data for the newly selected account. |

### Postconditions

- All three charts reflect the portfolio of the newly selected account.
- The `portfolio` Zustand slice stores the new `accountId`.

---

## Insights Response Specification

The `insights` object is returned as part of the `GET /api/v1/portfolio/holdings` response. It is computed entirely by the Portfolio backend.

### `insights.assetClassBreakdown`

Used by Chart 1 (pie chart — Asset Class Breakdown).

| Field | Type | Description |
|-------|------|-------------|
| stockPercent | decimal | Total stock value as % of total portfolio value (`totalStockValue / totalPortfolioValue × 100`). |
| cashPercent | decimal | Cash balance as % of total portfolio value (`cashBalance / totalPortfolioValue × 100`). |
| totalPortfolioValue | decimal | Sum of all stock `currentValue` values plus cash `balance`. |

- Both values are `null` when `totalPortfolioValue` is zero.

### `insights.stockBreakdown`

Used by Chart 2 (pie chart — Stock Holdings Breakdown). One entry per stock position with `quantity > 0`.

| Field | Type | Description |
|-------|------|-------------|
| ticker | string | Stock ticker symbol. |
| currentValue | decimal | `quantity × currentPrice` for this position. |
| percentOfStockPortfolio | decimal | `currentValue / totalStockValue × 100`. Denominator is total stock value only — cash is excluded. |

- Empty array when no stock positions exist.
- `percentOfStockPortfolio` is `null` when `totalStockValue` is zero.

### `insights.unrealisedPnLContribution`

Used by Chart 3 (diverging bar chart — Unrealised P&L Contribution). One entry per stock position with `quantity > 0`.

| Field | Type | Description |
|-------|------|-------------|
| ticker | string | Stock ticker symbol. |
| unrealisedPnL | decimal | `(currentPrice - avgPrice) × quantity`. Positive when in profit, negative when at a loss. |

- Empty array when no stock positions exist.
- Frontend renders positive values as bars above the zero axis and negative values as bars below. Both are displayed on the same chart.
- Cash is excluded.

---

## Chart Rendering Rules

- **Consistent component**: All pie charts across the platform use the same chart component and colour palette.
- **Interactive**: Pie chart slices are hoverable — hovering a slice displays a tooltip with the label, absolute value (currency), and percentage.
- **Percentage labels**: Pie charts display percentage labels directly on or beside each slice.
- **Diverging bar chart**: Bars are labelled with the ticker and the absolute unrealised P&L value. Positive bars use the profit colour; negative bars use the loss colour (consistent with the `unrealisedPnL` colour convention used in the Holdings table).
- **Empty state**: Each chart independently displays its own empty state message when it has no data. Charts do not collapse — they occupy their layout space with the empty state.
- **No cash slice on Chart 2 and Chart 3**: Cash is never represented in the stock breakdown or P&L charts.

---

## Domain Models Involved

- **Position**: Read from the Portfolio backend. Fields used for insights: `ticker`, `quantity`, `avgPrice`. `currentPrice` is sourced from the Market Data `api/` interface (same bulk call as the Holdings tab).
- **MarketDataSnapshot**: Read via Market Data `api/` interface (bulk call). Provides `currentPrice` per ticker. Shared with the Holdings response computation.
- **Account**: `balance` and `currency` read via Ledger `api/` interface. Provides the cash component for Chart 1.
- **Session**: `userId` resolved server-side from session context.
