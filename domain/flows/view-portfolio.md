# View Portfolio

## Overview

Allows an authenticated user to view their current portfolio holdings for a selected account on the Portfolio page (`/portfolio`). The user selects an account from a dropdown; the frontend fetches the position data from the Portfolio backend service, which enriches it with live prices from the Market Data domain and cash balance from the Ledger domain before returning a single priced response. The table renders one row per stock holding plus one cash row. All columns support client-side sorting; the default sort is by ticker, ascending alphabetically.

---

## Flow A — Load Portfolio Page

The user navigates to the Portfolio page. The frontend fetches the user's accounts and renders the account selector.

### Actors

- **Authenticated User**: A logged-in user navigating to `/portfolio`.
- **Guest Browser**: The React frontend rendering the Portfolio page.
- **System**: The Portfolio backend service.

### Preconditions

- The user has an active session (is logged in).
- The user's `status` is `active`.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Navigate to `/portfolio` | Arrives at the Portfolio page via the sidebar. |
| 2 | Guest Browser | Fetch active accounts | Calls `GET /api/v1/accounts?userId={userId}&status=active` (Ledger domain endpoint). |
| 3 | Guest Browser | Render account selector | Displays a dropdown of the user's active accounts. Each option shows `name` and `currency`. Accounts ordered by `createdAt` ascending. If no accounts exist, renders empty state: "No accounts available. Open an account first." |
| 4 | Guest Browser | Apply default account selection | If no account is stored in the `portfolio` Zustand slice, selects the first account in the list. If a selection is already stored, preserves it. |
| 5 | Guest Browser | Fetch portfolio holdings | Calls `GET /api/v1/portfolio/holdings?accountId={accountId}` with the selected account. See Flow B. |

### Postconditions

- The account selector is populated and a default account is selected.
- Portfolio holdings for the selected account are displayed.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Account fetch fails | `GET /api/v1/accounts` returns non-2xx | Selector shows error state: "Could not load accounts." Holdings table not rendered. |
| No active accounts | Account list is empty | Selector shows empty state. Holdings table not rendered. |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |

---

## Flow B — Fetch Priced Holdings

The backend Portfolio service receives a request for holdings for a given account, enriches the stored position data with live prices and cash balance, and returns a single composite response.

### Actors

- **Guest Browser**: The React frontend requesting portfolio data.
- **System (Portfolio)**: The Portfolio backend service.
- **System (Market Data)**: Called synchronously by the Portfolio service to retrieve current prices.
- **System (Ledger)**: Called synchronously by the Portfolio service to retrieve the current cash balance.

### Preconditions

- The selected account exists and belongs to the authenticated user.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest Browser | Request holdings | Calls `GET /api/v1/portfolio/holdings?accountId={accountId}`. Authenticated user's `userId` is resolved server-side from the session context. |
| 2 | System (Portfolio) | Validate request | Checks that `accountId` resolves to an account whose `userId` matches the authenticated user. Returns HTTP 403 if ownership check fails. Returns HTTP 404 if account not found. |
| 3 | System (Portfolio) | Load stock positions | Queries all `Position` rows where `accountId = {accountId}` and `quantity > 0`. |
| 4 | System (Portfolio) | Fetch live prices (bulk) | If any stock positions exist: calls the Market Data `api/` interface with the list of tickers from step 3. The Market Data domain returns the current `currentPrice` for each ticker from its in-memory `MarketDataSnapshot` cache in a single bulk call. |
| 5 | System (Portfolio) | Fetch cash balance | Calls the Ledger `api/` interface with `accountId` to retrieve the account's current `balance` and `currency`. |
| 6 | System (Portfolio) | Compute derived fields | For each stock position: computes `currentValue = quantity × currentPrice`; computes `unrealisedPnL = (currentPrice × quantity) - (avgPrice × quantity)`. The total portfolio value (`totalValue`) = sum of all `currentValue` values + cash `balance`. For each stock position: computes `portfolioPercent = currentValue / totalValue × 100`. The cash row `portfolioPercent = balance / totalValue × 100`. |
| 7 | System (Portfolio) | Build response | Constructs a response containing: a `holdings` array (one entry per stock position) and a `cash` object. Each stock holding entry includes: `ticker`, `quantity`, `currentPrice`, `currentValue`, `minPrice`, `maxPrice`, `avgPrice`, `portfolioPercent`, `unrealisedPnL`. The cash entry includes: `balance`, `currency`, `portfolioPercent`. |
| 8 | System (Portfolio) | Return HTTP 200 | Returns the priced holdings response. |
| 9 | Guest Browser | Render holdings table | Renders one row per stock holding plus one cash row. See table specification below. Default sort: `ticker` ascending. |

### Postconditions

- The holdings table displays up-to-date stock positions and cash balance for the selected account.
- All monetary values reflect prices from the Market Data cache at the time of the request.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Account not found | `accountId` does not resolve | System returns HTTP 404. Page shows: "Account not found." |
| Account not owned by user | Resolved account's `userId` ≠ session user | System returns HTTP 403. Page shows generic error message. |
| Market Data call fails | Bulk price fetch returns an error | System returns HTTP 502. Page shows: "Could not load portfolio. Price data unavailable." |
| Ledger cash fetch fails | Cash balance call returns an error | System returns HTTP 502. Page shows: "Could not load portfolio. Balance data unavailable." |
| No stock positions | Account has no `Position` rows with `quantity > 0` | `holdings` array is empty. Table renders cash row only. No error. |
| Total portfolio value is zero | Cash balance is zero and no stock positions | `portfolioPercent` is undefined (division by zero). All `portfolioPercent` values are rendered as `—`. |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |

---

## Flow C — Switch Account

The user selects a different account from the dropdown. The holdings table refreshes for the newly selected account.

### Actors

- **Authenticated User**: A logged-in user changing the selected account.
- **Guest Browser**: The React frontend re-fetching holdings for the new account.

### Preconditions

- The Portfolio page is loaded (Flow A has completed).
- The user has more than one active account.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Select a different account | Chooses a new account from the account selector dropdown. |
| 2 | Guest Browser | Store selection | Updates the selected `accountId` in the `portfolio` Zustand slice. |
| 3 | Guest Browser | Re-fetch holdings | Invalidates the TanStack Query cache for `GET /api/v1/portfolio/holdings` and re-fetches using the new `accountId`. Shows a loading state while the fetch is in progress. |
| 4 | Guest Browser | Re-render table | Replaces the table contents with the holdings for the newly selected account. |

### Postconditions

- The holdings table reflects the portfolio of the newly selected account.
- The `portfolio` Zustand slice stores the new `accountId`.

---

## Flow D — Sort Holdings Table

The user clicks a column header to sort the table. Sorting is client-side only — no new API call is made.

### Actors

- **Authenticated User**: A logged-in user sorting the portfolio table.
- **Guest Browser**: The React frontend applying the sort.

### Preconditions

- The Portfolio page is loaded and the holdings table is populated (Flow B has completed).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click a column header | Clicks any sortable column header in the holdings table. |
| 2 | Guest Browser | Apply client-side sort | Sorts the full loaded holdings list by the selected column. Cycles: ascending → descending → default (ticker ascending) on repeated clicks on the same column. |
| 3 | Guest Browser | Update column header | Displays a sort indicator (arrow) on the active column reflecting the current direction. |

### Postconditions

- The table is re-ordered by the selected column without a new API call.

---

## Portfolio Table Specification

| Column | Source | Notes |
|--------|--------|-------|
| Ticker | `Position.ticker` | Stock rows only. Cash row displays the account currency (e.g. `USD`). |
| Shares | `Position.quantity` | Stock rows only. Cash row displays `—`. |
| Current Value | `quantity × currentPrice` (stock) / `balance` (cash) | Monetary value in the account's base currency. |
| Min Share Price | `Position.minPrice` | Stock rows only. Cash row displays `—`. |
| Max Share Price | `Position.maxPrice` | Stock rows only. Cash row displays `—`. |
| Avg Bought Price | `Position.avgPrice` | Stock rows only. Cash row displays `—`. |
| % of Portfolio | `currentValue / totalValue × 100` | Shown for all rows including cash. Rendered as `—` if `totalValue = 0`. |
| Unrealised P&L | `(currentPrice - avgPrice) × quantity` | Stock rows only. Cash row displays `—`. Positive values in green, negative in red. |

- All monetary columns display values to 2 decimal places with the account's base currency symbol.
- The cash row is always rendered last, regardless of active sort (it is pinned to the bottom).
- Default sort: `ticker` ascending (alphabetical). Cash row remains pinned at the bottom.
- All columns except the cash-only fields (`Shares`, `Min Share Price`, `Max Share Price`, `Avg Bought Price`, `Unrealised P&L`) support sorting; clicking those column headers sorts the stock rows only — the cash row remains pinned.

---

## Domain Models Involved

- **Position**: Read at Flow B step 3. Fields used: `ticker`, `quantity`, `avgPrice`, `minPrice`, `maxPrice`.
- **MarketDataSnapshot**: Read at Flow B step 4 via Market Data `api/` interface (bulk call). `currentPrice` sourced from the in-memory cache.
- **Account**: Validated at Flow B step 2 for ownership. `balance` and `currency` read at Flow B step 5 via Ledger `api/` interface.
- **Session**: `userId` resolved server-side from session context at Flow B step 1. `accountId` stored in the `portfolio` Zustand slice on the frontend.
