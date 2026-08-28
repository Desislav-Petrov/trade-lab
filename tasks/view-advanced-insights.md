# Tasks: view-advanced-insights (Issue #146)

## Use Case Summary

An authenticated user views a step-line price history chart in the **Advanced Insights** tab on the Portfolio page. The chart shows every individual BUY (green dot) and SELL (red dot) fill per stock symbol over time. Symbols are toggleable via a legend. All chart data is served by the Portfolio backend from a new `PositionFill` read model populated by consuming the existing `OrderFilledEvent`.

**Flows:** `domain/flows/view-advanced-insights.md` (Flows A, B, C)  
**Models:** `domain/model/position-fill.md` (new), `domain/model/session.md`  
**Decision log:** `decisions/2026-08-28-portfolio-position-fill-read-model.md`

---

## Dependency Summary

| Task ID | Title | Depends on |
|---|---|---|
| DB-1 | Create PositionFill JPA entity | none |
| REPO-1 | Create PositionFillRepository | DB-1 |
| SVC-1 | Extend PortfolioService to persist PositionFill on OrderFilledEvent | REPO-1 |
| SVC-2 | Add getFillHistory service method | REPO-1 |
| EVT-1 | Extend StockTradingEventListener for PositionFill recording | SVC-1 |
| API-CONTRACT-1 | Add GET /portfolio/fills to portfolio-openapi.yaml | none |
| CONTROLLER-1 | Implement getFills delegate method | SVC-2, API-CONTRACT-1 |
| CLI-1 | Add fetchFillHistory API client function | API-CONTRACT-1 |
| STATE-1 | Add useFillHistory TanStack Query hook | CLI-1 |
| STATE-2 | Add symbol visibility client state to portfolio Zustand slice | none |
| COMP-1 | Build FillHistoryChart component | CLI-1, STATE-2 |
| COMP-2 | Build AdvancedInsightsTab component | COMP-1, STATE-1, STATE-2 |
| SCREEN-1 | Wire AdvancedInsightsTab into PortfolioPage | COMP-2 |

---

## Database Layer

### DB-1 — Create PositionFill JPA entity

**Layer:** Database  
**Domain:** portfolio  
**Use case:** view-advanced-insights  
**Implements:** `domain/model/position-fill.md` — full field specification; `RecordFill` persistence contract  
**Inputs:**  
- `domain/model/position-fill.md` — field definitions  
**Outputs:**  
- `org.dpp.tradelab.portfolio.model.PositionFill` — JPA entity class  
- `org.dpp.tradelab.portfolio.model.FillSide` — enum (`BUY`, `SELL`) _(add only if not already present in the portfolio model package)_  
**Acceptance criteria:**  
- [ ] `PositionFill` is a plain `class` (not `data class`) annotated `@Entity @Table(name = "position_fills")`.  
- [ ] Fields: `id: UUID`, `userId: UUID`, `accountId: UUID`, `ticker: String`, `assetType: AssetType`, `side: FillSide`, `executionPrice: BigDecimal`, `quantity: BigDecimal`, `filledAt: Instant`, `idempotencyKey: UUID` — all mapped with `@Column`.  
- [ ] `executionPrice` and `quantity` mapped with `@Column(precision = 19, scale = 4)`.  
- [ ] `assetType` and `side` mapped with `@Enumerated(EnumType.STRING)`.  
- [ ] `id` is `@Id @Column(nullable = false, updatable = false)` — no `@GeneratedValue`.  
- [ ] `idempotencyKey` has `@Column(unique = true)`.  
- [ ] Entity implements `Persistable<UUID>` with `@Transient _isNew: Boolean = true` flag.  
- [ ] `equals` and `hashCode` based on `id` only; `toString` implemented.  
- [ ] `FillSide` enum has values `BUY` and `SELL`.  
- [ ] No imports from `stocktrading.*`, `ledger.*`, or any domain other than `portfolio`.  
**Depends on:** none

---

## Repository Layer

### REPO-1 — Create PositionFillRepository

**Layer:** Repository  
**Domain:** portfolio  
**Use case:** view-advanced-insights  
**Implements:** `domain/flows/view-advanced-insights.md` Flow A — Step 3: query all fills for a given `accountId` and `userId` ordered by `filledAt` ascending  
**Inputs:**  
- `PositionFill` entity (DB-1)  
**Outputs:**  
- `org.dpp.tradelab.portfolio.repository.PositionFillRepository` — Spring Data JPA interface  
**Acceptance criteria:**  
- [ ] Interface extends `JpaRepository<PositionFill, UUID>`.  
- [ ] Custom query method: `findByUserIdAndAccountIdOrderByFilledAtAsc(userId: UUID, accountId: UUID): List<PositionFill>`.  
- [ ] No business logic in the interface.  
- [ ] Repository test using `@SpringBootTest` + `@AutoConfigureTestEntityManager` + `@Transactional` with H2 covers: results ordered by `filledAt` ascending, results scoped to both `userId` and `accountId`.  
**Depends on:** DB-1

---

## Service Layer

### SVC-1 — Extend PortfolioService to persist PositionFill on OrderFilledEvent

**Layer:** Service  
**Domain:** portfolio  
**Use case:** view-advanced-insights  
**Implements:** `domain/model/position-fill.md` — `RecordFill` behaviour; idempotency via `portfolio_processed_events` log  
**Inputs:**  
- `OrderFilledEvent` payload: `orderId`, `accountId`, `userId`, `ticker`, `quantity`, `side`, `executionPrice`, `timestamp`, `idempotencyKey`  
- `PositionFillRepository` (REPO-1)  
**Outputs:**  
- `handleOrderFilled` method on `PortfolioService` extended (or a new overload) to additionally insert a `PositionFill` row within the same transaction as the existing `Position` update  
**Acceptance criteria:**  
- [ ] Within the same `@Transactional` method that updates `Position`, a `PositionFill` row is inserted for every `OrderFilledEvent` (both `BUY` and `SELL` side).  
- [ ] Idempotency: if the `idempotencyKey` is already present in `portfolio_processed_events`, no `PositionFill` is inserted and the method returns silently. Key recording and insert happen in the same transaction.  
- [ ] `PositionFill.id` assigned via `UUID.randomUUID()` in the service before entity construction.  
- [ ] `PositionFill.filledAt` set from `event.timestamp`.  
- [ ] `PositionFill.side` set from `event.side`.  
- [ ] No imports from `stocktrading.*` in the service.  
- [ ] Unit tests (KoTest + mockito-kotlin): BUY fill persisted correctly, SELL fill persisted correctly, duplicate idempotency key → no insert, no exception.  
**Depends on:** REPO-1

---

### SVC-2 — Add getFillHistory service method

**Layer:** Service  
**Domain:** portfolio  
**Use case:** view-advanced-insights  
**Implements:** `domain/flows/view-advanced-insights.md` Flow A — Steps 3–4: query `PositionFill` rows and return grouped fill history  
**Inputs:**  
- `accountId: UUID`  
- `userId: UUID` (resolved from session context by the controller)  
- `PositionFillRepository` (REPO-1)  
**Outputs:**  
- `getFillHistory(userId: UUID, accountId: UUID): Map<String, List<PositionFill>>` on `PortfolioService` — fills grouped by `ticker`, ordered by `filledAt` ascending within each group  
**Acceptance criteria:**  
- [ ] Method annotated `@Transactional(readOnly = true)`.  
- [ ] Calls `PositionFillRepository.findByUserIdAndAccountIdOrderByFilledAtAsc`.  
- [ ] Groups results by `ticker` (preserving `filledAt` ordering within each group).  
- [ ] Returns an empty map (not an error) when no fills exist.  
- [ ] Unit tests: fills returned and grouped by ticker correctly, empty result returns empty map.  
**Depends on:** REPO-1

---

## Event Layer

### EVT-1 — Extend StockTradingEventListener for PositionFill recording

**Layer:** Event  
**Domain:** portfolio  
**Use case:** view-advanced-insights  
**Implements:** `domain/flows/view-advanced-insights.md` — `RecordFill` triggered on `OrderFilledEvent` consumption  
**Inputs:**  
- Existing `StockTradingEventListener` (or equivalent) in `portfolio.messaging`  
- Updated `PortfolioService.handleOrderFilled` (SVC-1)  
**Outputs:**  
- No new listener class. Confirm (or correct) that the existing `OrderFilledEvent` listener method delegates to one `handle*` method only — `SVC-1` extended that method's internals.  
- Confirm the listener annotation (`@EventListener` or `@TransactionalEventListener`) is consistent with the existing Position update phase.  
**Acceptance criteria:**  
- [ ] The `OrderFilledEvent` listener method in `portfolio.messaging` calls exactly one `handle*` service method — no business logic in the listener itself.  
- [ ] The listener annotation is `@Component` (not `@Service`), receives services via constructor injection, and has no state.  
- [ ] Unit test: listener method calls `handleOrderFilled` with the correct event payload; no other calls made.  
**Depends on:** SVC-1

---

## Controller Layer

### CONTROLLER-1 — Implement getFills delegate method

**Layer:** Controller  
**Domain:** portfolio  
**Use case:** view-advanced-insights  
**Implements:** `domain/flows/view-advanced-insights.md` Flow A — Steps 2–4: `GET /api/v1/portfolio/fills?accountId={accountId}`  
**Inputs:**  
- `accountId: UUID` — from query parameter  
- `userId: UUID` — resolved from authenticated session context  
- `PortfolioService.getFillHistory` (SVC-2)  
- Generated `PortfolioApiDelegate` updated by API-CONTRACT-1  
**Outputs:**  
- Implementation of `getFills` operation on `PortfolioApiDelegateImpl` in `portfolio.controller`  
- Returns `ResponseEntity<FillHistoryResponse>` (generated DTO) with HTTP 200  
**Acceptance criteria:**  
- [ ] Delegate method maps `getFillHistory` result to the generated `FillHistoryResponse` DTO — no hand-written DTOs.  
- [ ] Each ticker's fills are mapped to a `FillHistoryEntry` with `ticker` and a `dataPoints` list.  
- [ ] Each data point contains `filledAt`, `executionPrice`, `quantity`, `side`.  
- [ ] Returns HTTP 200 with an empty `fills` array when no fills exist — not an error response.  
- [ ] Returns HTTP 401 when session is invalid (handled by existing auth filter — no extra code needed).  
- [ ] No business logic in the delegate — entirely delegates to the service.  
- [ ] `@SpringBootTest` + MockMvc tests: 200 with fills populated, 200 with empty fills array, 401 unauthenticated.  
**Depends on:** SVC-2, API-CONTRACT-1

---

## API Contract Layer

### API-CONTRACT-1 — Add GET /portfolio/fills to portfolio-openapi.yaml

**Layer:** OpenAPI Contract  
**Domain:** portfolio  
**Use case:** view-advanced-insights  
**Implements:** `domain/flows/view-advanced-insights.md` — Fill History Response Specification  
**Inputs:**  
- Path: `GET /portfolio/fills`  
- Query parameter: `accountId` (uuid, required)  
- Response schemas: `FillHistoryResponse`, `FillHistoryEntry`, `FillDataPoint`  
- `FillDataPoint` fields: `filledAt: datetime`, `executionPrice: number`, `quantity: number`, `side: enum[BUY, SELL]`  
- Error responses: 401, 403  
**Outputs:**  
- `services/contract/portfolio-openapi.yaml` — updated with new path and three new component schemas  
**Acceptance criteria:**  
- [ ] `GET /portfolio/fills` added under `paths` with `operationId: getFills` and tag `Portfolio`.  
- [ ] `accountId` query parameter: `type: string, format: uuid, required: true`.  
- [ ] 200 response references `FillHistoryResponse`.  
- [ ] `FillHistoryResponse`: `required: [fills]`, `fills` is an array of `FillHistoryEntry`.  
- [ ] `FillHistoryEntry`: `required: [ticker, dataPoints]`.  
- [ ] `FillDataPoint`: `required: [filledAt, executionPrice, quantity, side]`. `side` is `type: string, enum: [BUY, SELL]`. `filledAt` is `type: string, format: date-time`. `executionPrice` and `quantity` are `type: number`.  
- [ ] 401 and 403 responses reference the existing `ErrorResponse` schema.  
- [ ] No existing paths or schemas are modified.  
- [ ] YAML is valid OpenAPI 3.0.3.  
**Depends on:** none

---

## API Client Layer (Frontend)

### CLI-1 — Add fetchFillHistory API client function

**Layer:** API Client  
**Domain:** portfolio  
**Use case:** view-advanced-insights  
**Implements:** `domain/flows/view-advanced-insights.md` Flow A — Step 2: `GET /api/v1/portfolio/fills?accountId={accountId}`  
**Inputs:**  
- `accountId: string` (UUID)  
- Shared Axios instance from `shared/api/`  
- API-CONTRACT-1 — endpoint shape and response schema  
**Outputs:**  
- `fetchFillHistory(accountId: string): Promise<FillHistoryResponse>` in `services/front-end/src/domains/portfolio/api/`  
- TypeScript interfaces `FillHistoryResponse`, `FillHistoryEntry`, `FillDataPoint`, `FillSide` in `services/front-end/src/domains/portfolio/types/`  
- `FILL_HISTORY_QUERY_KEY` cache key constant exported from the api module  
**Acceptance criteria:**  
- [ ] Uses shared Axios instance — no new Axios instance created.  
- [ ] `FillSide` is `type FillSide = 'BUY' | 'SELL'`.  
- [ ] `FillDataPoint.filledAt` typed as `string` (UTC ISO 8601 — timezone conversion only at display layer).  
- [ ] `fetchFillHistory` calls `GET /api/v1/portfolio/fills` with `accountId` as a query param.  
- [ ] No endpoint URLs, HTTP methods, or payload shapes invented outside the OpenAPI contract.  
- [ ] Test: `vi.mock` the Axios instance; assert correct URL, query param, and typed response returned.  
**Depends on:** API-CONTRACT-1

---

## State Layer (Frontend)

### STATE-1 — Add useFillHistory TanStack Query hook

**Layer:** State  
**Domain:** portfolio  
**Use case:** view-advanced-insights  
**Implements:** `domain/flows/view-advanced-insights.md` Flow A — Step 2 (fetch on tab activation); Flow C — Step 3 (re-fetch on account switch)  
**Inputs:**  
- `accountId: string | null`  
- `fetchFillHistory` (CLI-1)  
- `FILL_HISTORY_QUERY_KEY` (CLI-1)  
**Outputs:**  
- `useFillHistory(accountId: string | null)` hook in `services/front-end/src/domains/portfolio/hooks/`  
- Returns `{ data, isLoading, isError }`  
**Acceptance criteria:**  
- [ ] Query is enabled only when `accountId` is non-null.  
- [ ] Query key includes `accountId` — switching accounts automatically invalidates and re-fetches.  
- [ ] `isLoading: true` while fetching; `isError: true` on non-2xx.  
- [ ] No Zustand state used for server data.  
- [ ] Tests (`renderHook`): happy path returns data, null `accountId` does not trigger a fetch, error state set on API failure.  
**Depends on:** CLI-1

---

### STATE-2 — Add symbol visibility client state to portfolio Zustand slice

**Layer:** State  
**Domain:** portfolio  
**Use case:** view-advanced-insights  
**Implements:** `domain/flows/view-advanced-insights.md` Flow B — Steps 2–3: toggle symbol visibility client-side; Flow C — Step 4: reset visibility on account switch  
**Inputs:**  
- Existing portfolio Zustand slice in `services/front-end/src/domains/portfolio/hooks/`  
**Outputs:**  
- `hiddenSymbols: Set<string>` field added to the portfolio Zustand slice  
- `toggleSymbolVisibility(ticker: string): void` action  
- `resetSymbolVisibility(): void` action  
**Acceptance criteria:**  
- [ ] `hiddenSymbols` initialises as an empty `Set`.  
- [ ] `toggleSymbolVisibility` adds `ticker` if not present; removes it if present.  
- [ ] `resetSymbolVisibility` clears the set back to empty.  
- [ ] No server data stored in Zustand.  
- [ ] Tests: toggle adds ticker, second toggle removes ticker, reset clears all.  
**Depends on:** none

---

## Component Layer (Frontend)

### COMP-1 — Build FillHistoryChart component

**Layer:** Component  
**Domain:** portfolio  
**Use case:** view-advanced-insights  
**Implements:** `domain/flows/view-advanced-insights.md` Flow A — Step 5 (render chart); Flow B — Step 3 (re-render on toggle); Chart Rendering Rules  
**Inputs:**  
- `fills: FillHistoryEntry[]` — from `useFillHistory` hook  
- `hiddenSymbols: Set<string>` — from Zustand slice  
- `onToggleSymbol: (ticker: string) => void` — callback  
**Outputs:**  
- `FillHistoryChart` component in `services/front-end/src/domains/portfolio/components/`  
- `FillHistoryChartProps` TypeScript interface  
**Acceptance criteria:**  
- [ ] Renders one step line per ticker. Lines connect dots chronologically with horizontal-then-vertical steps (no smooth interpolation).  
- [ ] X-axis: time (`filledAt`, formatted as local date/time). Y-axis: execution price (`executionPrice`, formatted as currency).  
- [ ] BUY fill dots are green; SELL fill dots are red. Dot colour overrides the line colour for the dot only.  
- [ ] Each symbol's line uses a distinct colour from the platform's shared chart colour palette.  
- [ ] Hovering a dot shows a Shadcn `Tooltip` with: ticker, side, executionPrice (currency), quantity, filledAt (local date/time).  
- [ ] Legend has one entry per ticker. Clicking calls `onToggleSymbol`. Hidden symbols' lines and dots are not rendered; their legend entry is visually dimmed.  
- [ ] When `fills` is empty, renders empty state: "No trade history to display."  
- [ ] No API calls inside the component.  
- [ ] Tests: correct number of lines rendered, BUY/SELL dot colour distinction, empty state shown when fills empty, legend click calls `onToggleSymbol`.  
**Depends on:** CLI-1 (for types), STATE-2 (for `hiddenSymbols` type)

---

### COMP-2 — Build AdvancedInsightsTab component

**Layer:** Component  
**Domain:** portfolio  
**Use case:** view-advanced-insights  
**Implements:** `domain/flows/view-advanced-insights.md` Flow A — Steps 1–5 (tab shell: loading, error, empty, chart); Flow C — Step 3 (loading state on account switch)  
**Inputs:**  
- `accountId: string | null`  
- `useFillHistory` hook (STATE-1)  
- `hiddenSymbols`, `toggleSymbolVisibility`, `resetSymbolVisibility` from portfolio Zustand slice (STATE-2)  
- `FillHistoryChart` component (COMP-1)  
**Outputs:**  
- `AdvancedInsightsTab` component in `services/front-end/src/domains/portfolio/components/`  
**Acceptance criteria:**  
- [ ] Shows Shadcn `Skeleton` loading state while `isLoading` is true.  
- [ ] Shows Shadcn `Alert` error state "Could not load price history. Please try again." when `isError` is true.  
- [ ] On successful load, renders `FillHistoryChart` passing `fills`, `hiddenSymbols`, and `onToggleSymbol`.  
- [ ] When `accountId` prop changes, calls `resetSymbolVisibility()` — symbol visibility resets to all-on.  
- [ ] No API calls directly in the component.  
- [ ] Tests: loading → Skeleton rendered, error → Alert with correct message, success → FillHistoryChart rendered, accountId change → resetSymbolVisibility called.  
**Depends on:** COMP-1, STATE-1, STATE-2

---

## Screen Layer (Frontend)

### SCREEN-1 — Wire AdvancedInsightsTab into PortfolioPage

**Layer:** Screen  
**Domain:** portfolio  
**Use case:** view-advanced-insights  
**Implements:** `domain/flows/view-advanced-insights.md` Flow A — Step 1 (tab navigation); Flow C — Steps 1–4 (account switch refreshes tab)  
**Inputs:**  
- Existing `PortfolioPage` at `services/front-end/src/domains/portfolio/pages/`  
- `AdvancedInsightsTab` component (COMP-2)  
- `selectedAccountId` from portfolio Zustand slice  
**Outputs:**  
- `PortfolioPage` updated: "Advanced Insights" `TabsContent` wired to `<AdvancedInsightsTab accountId={selectedAccountId} />`  
**Acceptance criteria:**  
- [ ] "Advanced Insights" tab renders `AdvancedInsightsTab` with the current `selectedAccountId`.  
- [ ] No placeholder text or empty shell remains for this tab.  
- [ ] Tab label and routing behaviour are unchanged.  
- [ ] When the account selector changes, the new `selectedAccountId` flows through to `AdvancedInsightsTab` automatically via props.  
- [ ] Page test: "Advanced Insights" tab renders `AdvancedInsightsTab`; account selector change passes new `accountId` to the tab.  
**Depends on:** COMP-2
