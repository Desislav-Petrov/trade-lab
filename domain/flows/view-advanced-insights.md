# View Advanced Insights

## Overview

Covers the Advanced Insights tab on the Portfolio page (`/portfolio`). The tab renders a step-line price history chart for the selected account, showing every individual fill (BUY and SELL) for each held or previously held stock symbol over time. Each symbol is drawn as a separate step line. The user can toggle individual symbols on and off by clicking them. All chart data is served by the Portfolio backend from the `PositionFill` read model — the frontend performs no calculations.

The fill history endpoint is paginated. Each page returns up to 100 fills, ordered by `filledAt` ascending. The frontend fetches all pages before rendering the chart.

---

## Flow A — Load Advanced Insights Tab

The user navigates to the Advanced Insights tab. The frontend fetches the fill history for the selected account (all pages) and renders the step-line chart.

### Actors

- **Authenticated User**: A logged-in user navigating to the Advanced Insights tab.
- **Guest Browser**: The React frontend rendering the chart.
- **System (Portfolio)**: The Portfolio backend service querying and returning fill history.

### Preconditions

- The user has an active session (is logged in).
- The user's `status` is `active`.
- The page-level account selector has resolved to a default account (see `domain/flows/view-portfolio` Flow A).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click "Advanced Insights" tab | Activates the Advanced Insights tab on the Portfolio page. |
| 2 | Guest Browser | Fetch fill history (first page) | Calls `GET /api/v1/portfolio/fills?accountId={accountId}&page=0&size=100`. |
| 3 | System (Portfolio) | Query PositionFill rows (paginated) | Retrieves up to 100 `PositionFill` rows for the given `accountId` scoped to the authenticated user's `userId`, ordered by `filledAt` ascending. Returns a page of results with pagination metadata (`totalPages`, `totalElements`, `page`, `size`). |
| 4 | System (Portfolio) | Return HTTP 200 | Returns the current page of fill data grouped by `ticker`, plus pagination metadata. |
| 5 | Guest Browser | Fetch remaining pages | If `page + 1 < totalPages`, fetches subsequent pages sequentially until all pages are retrieved. Shows a loading state throughout. |
| 6 | Guest Browser | Render step-line chart | Once all pages are collected, renders one step line per ticker symbol. Time (`filledAt`) on the x-axis; execution price (`executionPrice`) on the y-axis. Each fill is plotted as a dot: green for `BUY`, red for `SELL`. Lines connect the dots in chronological order as a step line (no interpolation — the line steps horizontally then vertically). All symbols are visible by default. |

### Postconditions

- The step-line chart is rendered with one line per symbol, using all fills across all pages.
- All symbols are toggled on by default.
- Each fill dot is coloured green (BUY) or red (SELL).

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Fill history fetch fails | `GET /api/v1/portfolio/fills` returns non-2xx on any page | Tab shows: "Could not load price history. Please try again." No chart rendered. |
| No fills exist | `totalElements` is zero | Tab shows empty state: "No trade history to display." No chart rendered. |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |

---

## Flow B — Toggle Symbol Visibility

The user clicks a symbol label in the chart legend to hide or show that symbol's step line.

### Actors

- **Authenticated User**: A logged-in user interacting with the chart legend.
- **Guest Browser**: The React frontend updating chart rendering state.

### Preconditions

- The Advanced Insights tab is loaded and the chart is rendered with at least one symbol.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click symbol in legend | Clicks a ticker label in the chart legend. |
| 2 | Guest Browser | Toggle visibility state | Toggles the visibility flag for that symbol in local component state. No API call is made. |
| 3 | Guest Browser | Re-render chart | Redraws the chart: hidden symbols' step lines and dots are removed from the canvas. The legend entry for the hidden symbol is visually dimmed. |

### Postconditions

- The clicked symbol's line and dots are hidden (if previously visible) or shown (if previously hidden).
- All other symbols are unaffected.
- No server request is made.

---

## Flow C — Switch Account on Advanced Insights Tab

The user selects a different account from the page-level account selector while on the Advanced Insights tab. The chart refreshes for the newly selected account.

### Actors

- **Authenticated User**: A logged-in user changing the selected account.
- **Guest Browser**: The React frontend re-fetching fill history for the new account.

### Preconditions

- The Advanced Insights tab is loaded.
- The user has more than one active account.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Select a different account | Chooses a new account from the page-level account selector dropdown. |
| 2 | Guest Browser | Store selection | Updates the selected `accountId` in the `portfolio` Zustand slice. |
| 3 | Guest Browser | Re-fetch fill history | Invalidates the TanStack Query cache for `GET /api/v1/portfolio/fills` and re-fetches all pages using the new `accountId`. Shows a loading state on the chart while fetching. |
| 4 | Guest Browser | Re-render chart | Replaces the chart with data for the newly selected account. All symbols are toggled on by default after a reload. |

### Postconditions

- The chart reflects the fill history of the newly selected account.
- Symbol visibility resets to all-on.

---

## Fill History Response Specification

Returned by `GET /api/v1/portfolio/fills?accountId={accountId}&page={page}&size={size}`.

### Query parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|:--------:|---------|-------------|
| accountId | uuid | yes | — | The account to retrieve fills for. |
| page | integer | no | 0 | Zero-based page index. |
| size | integer | no | 100 | Page size. Maximum 100. |

### Response structure

```
page:        integer   — zero-based current page index
size:        integer   — number of items in this page
totalPages:  integer   — total number of pages
totalElements: integer — total number of fills across all pages
fills: [
  {
    ticker: string,
    dataPoints: [
      {
        filledAt: datetime,
        executionPrice: decimal,
        quantity: decimal,
        side: "BUY" | "SELL"
      }
    ]
  }
]
```

- `fills` contains only the tickers that have at least one fill in this page's result set.
- `dataPoints` within each ticker group are ordered by `filledAt` ascending.
- `side` drives dot colour on the frontend: `BUY` → green; `SELL` → red.
- The step line connects consecutive data points regardless of `side`.
- Maximum page size is 100 fills per request.

---

## Chart Rendering Rules

- **Chart type**: Step line. Lines connect dots with a horizontal-then-vertical step (no smooth interpolation).
- **Axes**: Time (`filledAt`) on the x-axis; execution price (`executionPrice`) on the y-axis.
- **Dots**: Every data point is rendered as a dot on the line. Green for `BUY`; red for `SELL`. Dot size is uniform across symbols.
- **Colours**: Each symbol's step line has a distinct colour drawn from the platform's shared chart colour palette. Dot fill colour (green/red) overrides the line colour for the dot itself.
- **Legend**: One entry per symbol. Clicking a legend entry toggles that symbol's visibility (Flow B).
- **Tooltip**: Hovering a dot displays a tooltip with: ticker, side, executionPrice (formatted as currency), quantity, and filledAt (formatted as date/time).
- **Empty state**: Displayed when no fill data exists for the selected account. No chart is rendered.
- **Loading state**: A loading indicator replaces the chart area while data is being fetched (including subsequent pages).
- **No cash**: Cash is never represented on this chart.

---

## Domain Models Involved

- **PositionFill**: Primary data source. Queried by `accountId` and `userId`, paginated by `filledAt` ascending. Fields used: `ticker`, `side`, `executionPrice`, `quantity`, `filledAt`.
- **Session**: `userId` resolved server-side from session context to scope the fill query to the authenticated user.
