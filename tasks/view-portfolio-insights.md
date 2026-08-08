# Tasks: view-portfolio-insights

**Use case:** `domain/usecases/view-portfolio.md`  
**Domain:** portfolio  
**Flows:** `domain/flows/view-portfolio` (Flows A–F), `domain/flows/view-portfolio-insights` (Flows A–B)  
**Issue:** #109

---

## Pre-conditions for implementers

The following tasks from `tasks/view-portfolio.md` must already be implemented and merged before starting any task here:

- `SVC-2` — `PortfolioQueryService.getHoldings` exists and is the service method being extended.
- `CONTROLLER-1` — `PortfolioApiDelegateImpl.getHoldings` exists and maps the service result to the generated response DTO.
- `API-CONTRACT-1` — `services/contract/portfolio-openapi.yaml` exists with `PortfolioHoldingsResponse`, `StockHolding`, `CashHolding` already defined.
- `CLI-1` — `portfolioApi.ts` and `portfolio.types.ts` exist with `PortfolioHoldingsResponse`, `StockHolding`, `CashHolding` typed.
- `STATE-2` — `usePortfolioHoldings` TanStack Query hook exists.
- `SCREEN-1` — `PortfolioPage` exists at `/portfolio` with `PortfolioAccountSelector` and `PortfolioHoldingsTable`.

---

## Layer: SVC — Service

### [SVC-1] — Extend PortfolioQueryService to compute insight aggregations

**Layer:** Service  
**Domain:** portfolio  
**Use case:** view-portfolio-insights  
**Implements:** `view-portfolio` Flow B — step 7; `view-portfolio-insights` — Insights Response Specification  
**Inputs:**
- Existing computed per-stock data available after step 6 of `PortfolioQueryService.getHoldings`: list of `StockHoldingResult(ticker, quantity, currentPrice, avgPrice, currentValue, unrealisedPnL)`
- `cashBalance: BigDecimal` — already fetched from Ledger `api/`

**Outputs:**
- New internal data classes (not JPA entities) in `org.dpp.tradelab.portfolio.service`:
  - `PortfolioInsights(assetClassBreakdown: AssetClassBreakdown, stockBreakdown: List<StockBreakdownEntry>, unrealisedPnLContribution: List<UnrealisedPnLEntry>)`
  - `AssetClassBreakdown(stockPercent: BigDecimal?, cashPercent: BigDecimal?, totalPortfolioValue: BigDecimal)`
  - `StockBreakdownEntry(ticker: String, currentValue: BigDecimal, percentOfStockPortfolio: BigDecimal?)`
  - `UnrealisedPnLEntry(ticker: String, unrealisedPnL: BigDecimal)`
- `PortfolioHoldingsResult` extended to include `insights: PortfolioInsights`
- `PortfolioQueryService.getHoldings` updated to compute and include `insights` in its return value

**Acceptance criteria:**
- [ ] `assetClassBreakdown.stockPercent = totalStockValue / totalPortfolioValue × 100` where `totalPortfolioValue = totalStockValue + cashBalance`. Both `stockPercent` and `cashPercent` are `null` when `totalPortfolioValue = 0`.
- [ ] `assetClassBreakdown.cashPercent = cashBalance / totalPortfolioValue × 100`. `null` when `totalPortfolioValue = 0`.
- [ ] `stockBreakdown` contains one entry per stock position. `percentOfStockPortfolio = currentValue / totalStockValue × 100`. `null` when `totalStockValue = 0`. Empty list when no stock positions exist.
- [ ] `unrealisedPnLContribution` contains one entry per stock position. `unrealisedPnL = (currentPrice - avgPrice) × quantity`. Positive and negative values both included. Empty list when no stock positions exist.
- [ ] Cash is never included in `stockBreakdown` or `unrealisedPnLContribution`.
- [ ] All `BigDecimal` arithmetic uses `BigDecimal` operations — no floating-point.
- [ ] No new repository calls — all inputs are already in memory from prior steps.
- [ ] Unit test: mixed positive and negative `unrealisedPnL` — both appear in result.
- [ ] Unit test: zero stock positions — `stockBreakdown` and `unrealisedPnLContribution` are empty lists; `assetClassBreakdown.stockPercent = 0` or `null` when total is zero.
- [ ] Unit test: zero total portfolio value — `stockPercent` and `cashPercent` are both `null`.
- [ ] Unit test: single stock holding — `percentOfStockPortfolio = 100`.
- [ ] Unit test: multiple stock holdings — all `percentOfStockPortfolio` values sum to 100.

**Depends on:** existing `SVC-2` from `tasks/view-portfolio.md`

---

## Layer: API-CONTRACT — OpenAPI Contract

### [API-CONTRACT-1] — Extend portfolio OpenAPI contract with insights schemas

**Layer:** API-Contract  
**Domain:** portfolio  
**Use case:** view-portfolio-insights  
**Implements:** `view-portfolio-insights` — Insights Response Specification; `view-portfolio` Flow B step 8  
**Inputs:**
- Existing `services/contract/portfolio-openapi.yaml` (already has `PortfolioHoldingsResponse`, `StockHolding`, `CashHolding`, `ErrorResponse`)
- `PortfolioInsights` structure from SVC-1

**Outputs:**
- Updated `services/contract/portfolio-openapi.yaml` with:
  - `PortfolioHoldingsResponse` extended with an `insights` field of type `PortfolioInsights`
  - Four new schemas added to `components/schemas`: `PortfolioInsights`, `AssetClassBreakdown`, `StockBreakdownEntry`, `UnrealisedPnLEntry`

**New schemas to add:**

```yaml
PortfolioInsights:
  type: object
  properties:
    assetClassBreakdown:
      $ref: '#/components/schemas/AssetClassBreakdown'
    stockBreakdown:
      type: array
      items:
        $ref: '#/components/schemas/StockBreakdownEntry'
    unrealisedPnLContribution:
      type: array
      items:
        $ref: '#/components/schemas/UnrealisedPnLEntry'

AssetClassBreakdown:
  type: object
  properties:
    stockPercent:
      type: number
      nullable: true
      example: 65.50
    cashPercent:
      type: number
      nullable: true
      example: 34.50
    totalPortfolioValue:
      type: number
      example: 6825.00

StockBreakdownEntry:
  type: object
  required:
    - ticker
    - currentValue
  properties:
    ticker:
      type: string
      example: AAPL
    currentValue:
      type: number
      example: 1825.00
    percentOfStockPortfolio:
      type: number
      nullable: true
      example: 72.30

UnrealisedPnLEntry:
  type: object
  required:
    - ticker
    - unrealisedPnL
  properties:
    ticker:
      type: string
      example: AAPL
    unrealisedPnL:
      type: number
      example: 225.00
```

**Acceptance criteria:**
- [ ] `PortfolioHoldingsResponse` gains an `insights` field of type `$ref: '#/components/schemas/PortfolioInsights'`. The field is optional (not in `required`).
- [ ] All four new schemas (`PortfolioInsights`, `AssetClassBreakdown`, `StockBreakdownEntry`, `UnrealisedPnLEntry`) are defined in `components/schemas`.
- [ ] `stockPercent`, `cashPercent`, and `percentOfStockPortfolio` are all `nullable: true`.
- [ ] No existing schemas (`StockHolding`, `CashHolding`, `ErrorResponse`) are modified.
- [ ] The YAML remains valid OpenAPI 3.0.3.
- [ ] The backend Gradle build passes (`./gradlew build`) after regeneration with the updated YAML.

**Depends on:** SVC-1

---

## Layer: CONTROLLER — Controller

### [CONTROLLER-1] — Update PortfolioApiDelegateImpl to map insights into the holdings response

**Layer:** Controller  
**Domain:** portfolio  
**Use case:** view-portfolio-insights  
**Implements:** `view-portfolio` Flow B — step 9 (Return HTTP 200 with extended response)  
**Inputs:**
- Extended `PortfolioHoldingsResult` from SVC-1 (now includes `insights: PortfolioInsights`)
- Generated `PortfolioInsights`, `AssetClassBreakdown`, `StockBreakdownEntry`, `UnrealisedPnLEntry` DTOs from API-CONTRACT-1

**Outputs:**
- Updated `PortfolioApiDelegateImpl.getHoldings` that sets `insights` on the `PortfolioHoldingsResponse` before returning HTTP 200

**Acceptance criteria:**
- [ ] `insights` field on `PortfolioHoldingsResponse` is populated from the service result.
- [ ] `assetClassBreakdown`, `stockBreakdown`, and `unrealisedPnLContribution` are all correctly mapped from internal service types to generated DTO types.
- [ ] Existing `holdings` and `cash` mapping is unchanged.
- [ ] MockMvc test: successful response body includes a non-null `insights` object with all three sub-fields (`assetClassBreakdown`, `stockBreakdown`, `unrealisedPnLContribution`).
- [ ] MockMvc test: `insights.stockBreakdown` is an empty array when no stock positions exist.
- [ ] MockMvc test: `insights.assetClassBreakdown.stockPercent` and `cashPercent` are `null` in the response when total portfolio value is zero.
- [ ] All existing CONTROLLER-1 tests from `tasks/view-portfolio.md` continue to pass.

**Depends on:** SVC-1, API-CONTRACT-1

---

## Layer: CLI — API Client

### [CLI-1] — Extend portfolio TypeScript types with insights response fields

**Layer:** API Client  
**Domain:** portfolio  
**Use case:** view-portfolio-insights  
**Implements:** `view-portfolio-insights` Flow A — step 2 (frontend consumes extended holdings response)  
**Inputs:**
- Updated `services/contract/portfolio-openapi.yaml` (from API-CONTRACT-1)
- Existing `services/front-end/src/domains/portfolio/types/portfolio.types.ts`

**Outputs:**
- Updated `portfolio.types.ts` with four new exported interfaces:
  - `PortfolioInsights`
  - `AssetClassBreakdown`
  - `StockBreakdownEntry`
  - `UnrealisedPnLEntry`
- `PortfolioHoldingsResponse` extended with `insights?: PortfolioInsights`

**Acceptance criteria:**
- [ ] `PortfolioInsights`, `AssetClassBreakdown`, `StockBreakdownEntry`, `UnrealisedPnLEntry` interfaces are exported from `portfolio.types.ts`.
- [ ] `PortfolioHoldingsResponse` includes `insights?: PortfolioInsights`.
- [ ] `stockPercent`, `cashPercent`, and `percentOfStockPortfolio` typed as `number | null`.
- [ ] `stockBreakdown` typed as `StockBreakdownEntry[]`.
- [ ] `unrealisedPnLContribution` typed as `UnrealisedPnLEntry[]`.
- [ ] No `any` used.
- [ ] Types match the OpenAPI schema exactly — field names, nullability, and array types.
- [ ] Existing types (`StockHolding`, `CashHolding`) are unchanged.

**Depends on:** API-CONTRACT-1 (read the updated YAML to derive types)

---

## Layer: STATE — State management

### [STATE-1] — Update usePortfolioHoldings hook to expose insights data

**Layer:** State  
**Domain:** portfolio  
**Use case:** view-portfolio-insights  
**Implements:** `view-portfolio-insights` Flow A — step 2; `view-portfolio` Flow B — step 10 (single cached fetch, both tabs)  
**Inputs:**
- Existing `usePortfolioHoldings` hook in `services/front-end/src/domains/portfolio/hooks/`
- Updated `PortfolioHoldingsResponse` type (from CLI-1) which now includes `insights?: PortfolioInsights`

**Outputs:**
- Updated `usePortfolioHoldings` hook that exposes `insights: PortfolioInsights | undefined` alongside the existing `holdings` and `cash` return fields

**Acceptance criteria:**
- [ ] `insights` is returned from the hook alongside `holdings` and `cash` — destructured from `data`.
- [ ] No second API call is made — Holdings tab and Insights tab both read from the same TanStack Query cache entry (same `queryKey`).
- [ ] Existing return fields (`holdings`, `cash`, `isLoading`, `isError`) are unchanged.
- [ ] Unit test (`renderHook`): `insights` is defined and populated when the API returns it.
- [ ] Unit test: `insights.stockBreakdown` is an empty array when the API returns no stock positions.
- [ ] Unit test: `insights` is `undefined` when `data` is `undefined` (loading or disabled state).

**Depends on:** CLI-1

---

## Layer: COMP — Components

### [COMP-1] — Build AssetClassPieChart component

**Layer:** Component  
**Domain:** portfolio  
**Use case:** view-portfolio-insights  
**Implements:** `view-portfolio-insights` Flow A — step 5; Chart Rendering Rules  
**Inputs:**
- `AssetClassPieChartProps`:
  - `data: AssetClassBreakdown | null | undefined`
  - `currency: string` — account base currency for tooltip value formatting

**Outputs:**
- `services/front-end/src/domains/portfolio/components/AssetClassPieChart.tsx`

**Acceptance criteria:**
- [ ] Renders a pie chart with two slices: **Stock** and **Cash**, each showing its percentage.
- [ ] When `data` is `null`, `undefined`, or `data.totalPortfolioValue === 0`: renders empty state message "No portfolio data to display." No chart rendered.
- [ ] When `stockPercent` or `cashPercent` is `null`, the corresponding slice label renders as `—`.
- [ ] Slices are interactive — hovering a slice shows a tooltip with: slice label, absolute value (currency-formatted using `currency` prop), and percentage.
- [ ] Percentage labels are displayed directly on or beside each slice.
- [ ] Uses the shared chart colour palette (consistent with other pie charts on the platform).
- [ ] Explicit `AssetClassPieChartProps` TypeScript interface.
- [ ] No API calls or store access inside the component.
- [ ] Unit test: renders two slices with correct labels when data is present.
- [ ] Unit test: renders empty state when `data` is `null`.
- [ ] Unit test: renders empty state when `totalPortfolioValue === 0`.

**Depends on:** CLI-1

---

### [COMP-2] — Build StockBreakdownPieChart component

**Layer:** Component  
**Domain:** portfolio  
**Use case:** view-portfolio-insights  
**Implements:** `view-portfolio-insights` Flow A — step 6; Chart Rendering Rules  
**Inputs:**
- `StockBreakdownPieChartProps`:
  - `data: StockBreakdownEntry[]`
  - `currency: string` — account base currency for tooltip value formatting

**Outputs:**
- `services/front-end/src/domains/portfolio/components/StockBreakdownPieChart.tsx`

**Acceptance criteria:**
- [ ] Renders one pie slice per `StockBreakdownEntry`, labelled with `ticker` and `percentOfStockPortfolio`.
- [ ] When `data` is an empty array: renders empty state "No stock holdings to display." No chart rendered.
- [ ] When `percentOfStockPortfolio` is `null` for any entry, that slice's label renders as `—`.
- [ ] Slices are interactive — hovering shows tooltip with: `ticker`, `currentValue` (currency-formatted), and `percentOfStockPortfolio`.
- [ ] Percentage labels displayed on or beside each slice.
- [ ] Uses the same shared chart colour palette as COMP-1.
- [ ] Cash is never represented as a slice.
- [ ] Explicit `StockBreakdownPieChartProps` TypeScript interface.
- [ ] No API calls or store access inside the component.
- [ ] Unit test: renders correct number of slices for multiple stocks.
- [ ] Unit test: renders empty state when `data` is empty.
- [ ] Unit test: single stock renders a single 100% slice.

**Depends on:** CLI-1

---

### [COMP-3] — Build UnrealisedPnLDivergingBarChart component

**Layer:** Component  
**Domain:** portfolio  
**Use case:** view-portfolio-insights  
**Implements:** `view-portfolio-insights` Flow A — step 7; Chart Rendering Rules  
**Inputs:**
- `UnrealisedPnLDivergingBarChartProps`:
  - `data: UnrealisedPnLEntry[]`
  - `currency: string` — account base currency for bar label formatting

**Outputs:**
- `services/front-end/src/domains/portfolio/components/UnrealisedPnLDivergingBarChart.tsx`

**Acceptance criteria:**
- [ ] Renders a diverging bar chart with a horizontal zero axis.
- [ ] Positive `unrealisedPnL` values render as bars **above** the zero axis.
- [ ] Negative `unrealisedPnL` values render as bars **below** the zero axis.
- [ ] Positive bars use the profit colour (green — same green used for positive `unrealisedPnL` in the Holdings table).
- [ ] Negative bars use the loss colour (red — same red used for negative `unrealisedPnL` in the Holdings table).
- [ ] Each bar is labelled with `ticker` and the absolute `unrealisedPnL` value (currency-formatted).
- [ ] When `data` is an empty array: renders empty state "No stock holdings to display." No chart rendered.
- [ ] Cash is never included.
- [ ] Explicit `UnrealisedPnLDivergingBarChartProps` TypeScript interface.
- [ ] No API calls or store access inside the component.
- [ ] Unit test: all positive P&L — all bars above axis with correct colour.
- [ ] Unit test: all negative P&L — all bars below axis with correct colour.
- [ ] Unit test: mixed positive and negative — both present in correct positions.
- [ ] Unit test: empty state when `data` is empty.

**Depends on:** CLI-1

---

### [COMP-4] — Build InsightsTab component

**Layer:** Component  
**Domain:** portfolio  
**Use case:** view-portfolio-insights  
**Implements:** `view-portfolio-insights` Flow A — steps 5–7; error and empty states per chart  
**Inputs:**
- `InsightsTabProps`:
  - `insights: PortfolioInsights | undefined`
  - `isLoading: boolean`
  - `isError: boolean`
  - `currency: string` — passed through to each chart

**Outputs:**
- `services/front-end/src/domains/portfolio/components/InsightsTab.tsx`

**Acceptance criteria:**
- [ ] Renders all three charts laid out vertically: `AssetClassPieChart`, `StockBreakdownPieChart`, `UnrealisedPnLDivergingBarChart`.
- [ ] While `isLoading` is `true`: renders a loading skeleton/spinner in each chart's layout area. No charts mounted.
- [ ] When `isError` is `true`: renders "Could not load insights. Please try again." No charts rendered.
- [ ] Each chart independently handles its own empty state — charts do not collapse when they have no data; they remain in the layout and display their own empty state message.
- [ ] `currency` is forwarded to each chart component.
- [ ] No API calls or store access inside the component — all data via props.
- [ ] Explicit `InsightsTabProps` TypeScript interface.
- [ ] Unit test: loading state renders skeleton, no charts.
- [ ] Unit test: error state renders error message, no charts.
- [ ] Unit test: normal render — all three chart components are mounted.
- [ ] Unit test: no stock positions — Chart 1 renders (cash-only), Charts 2 and 3 show empty state.

**Depends on:** COMP-1, COMP-2, COMP-3

---

## Layer: SCREEN — Screen

### [SCREEN-1] — Restructure PortfolioPage into tab layout with Insights as default tab

**Layer:** Screen  
**Domain:** portfolio  
**Use case:** view-portfolio-insights  
**Implements:** `view-portfolio` Flow A steps 5–6 (tab bar, default tab); Flow D (switch tab); `view-portfolio-insights` Flow B (account switch refreshes all tabs)  
**Inputs:**
- Existing `PortfolioPage` at `services/front-end/src/domains/portfolio/pages/PortfolioPage.tsx`
- Updated `usePortfolioHoldings` hook (STATE-1) — now returns `insights`
- `InsightsTab` component (COMP-4)
- Existing `PortfolioHoldingsTable` component (from `tasks/view-portfolio.md` COMP-2)
- Existing `PortfolioAccountSelector` component (from `tasks/view-portfolio.md` COMP-1)

**Outputs:**
- Updated `PortfolioPage` with:
  - Page-level account selector sitting **above** the tab bar (selector is always visible regardless of active tab)
  - Tab bar: **Insights** (default active on navigation to `/portfolio`) | **Holdings** | **Advanced Insights**
  - Insights tab renders `InsightsTab`
  - Holdings tab renders the existing `PortfolioHoldingsTable` (content unchanged — just moved inside the Holdings tab)
  - Advanced Insights tab renders an empty placeholder (no chart components, no error — just an empty content area)

**Acceptance criteria:**
- [ ] On navigation to `/portfolio`, the **Insights** tab is active by default.
- [ ] The account selector is positioned above the tab bar and is always visible regardless of which tab is active.
- [ ] Changing the account selector invalidates the TanStack Query cache for `usePortfolioHoldings` and re-fetches for the newly selected account. Both tabs reflect the new account's data.
- [ ] Switching between Insights and Holdings tabs does **not** trigger a new API call — the TanStack Query cache is reused.
- [ ] Advanced Insights tab renders without error — empty content area, no charts mounted.
- [ ] Holdings tab renders `PortfolioHoldingsTable` with the same behaviour as before restructuring.
- [ ] Loading and error states are passed to `InsightsTab` via props (`isLoading`, `isError`, `insights`).
- [ ] `currency` from the cash holding response is passed through to `InsightsTab`.
- [ ] Page contains no business logic — all logic in hooks and components.
- [ ] Unit test: default active tab is Insights on mount.
- [ ] Unit test: clicking Holdings tab renders Holdings table without new API call.
- [ ] Unit test: clicking Advanced Insights tab renders empty placeholder.
- [ ] Unit test: account selector change triggers `usePortfolioHoldings` re-fetch.
- [ ] Unit test: loading state is passed to `InsightsTab` while fetching.
- [ ] Unit test: error state is passed to `InsightsTab` on fetch failure.

**Depends on:** STATE-1, COMP-4

---

## Dependency Summary

| Task ID | Title | Depends On |
|---------|-------|------------|
| SVC-1 | Extend PortfolioQueryService with insight aggregations | existing SVC-2 (view-portfolio.md) |
| API-CONTRACT-1 | Extend portfolio OpenAPI contract with insights schemas | SVC-1 |
| CONTROLLER-1 | Map insights into holdings response | SVC-1, API-CONTRACT-1 |
| CLI-1 | Extend portfolio TypeScript types with insights fields | API-CONTRACT-1 |
| STATE-1 | Update usePortfolioHoldings to expose insights | CLI-1 |
| COMP-1 | AssetClassPieChart component | CLI-1 |
| COMP-2 | StockBreakdownPieChart component | CLI-1 |
| COMP-3 | UnrealisedPnLDivergingBarChart component | CLI-1 |
| COMP-4 | InsightsTab component | COMP-1, COMP-2, COMP-3 |
| SCREEN-1 | Restructure PortfolioPage into tab layout | STATE-1, COMP-4 |
