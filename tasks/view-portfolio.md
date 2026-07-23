# Tasks: view-portfolio

**Use case:** `domain/usecases/view-portfolio.md`
**Domain:** portfolio
**Flows:** `domain/flows/view-portfolio` (A–D), `domain/flows/aggregate-stock-position` (A)

---

## Pre-conditions for implementers

Before starting any task, confirm:

1. `LedgerApi` in `ledger.api` — does not yet expose `getBalance(accountId)`. Task **API-LEDGER-1** adds it.
2. `MarketDataApi` in `marketdata.api` — does not yet expose a bulk price lookup. Task **API-MARKETDATA-1** adds it.
3. `OrderFilledEvent` in `stocktrading.messaging` — missing `idempotencyKey` and `side` fields. Task **EVT-1** extends it.

---

## Layer: EVT — Event extension (stocktrading domain)

### EVT-1 — Extend OrderFilledEvent with idempotencyKey and side

**Layer:** Event
**Domain:** stocktrading
**Use case:** view-portfolio
**Implements:** aggregate-stock-position flow — Events Consumed note
**Inputs:**
- Existing `OrderFilledEvent` data class in `stocktrading.messaging`
- `Order.idempotencyKey: UUID` (already on entity)
- `OrderSide` enum values `BUY` and `SELL` (SELL anticipated for future)

**Outputs:**
- Updated `OrderFilledEvent` data class with two new fields: `idempotencyKey: UUID`, `side: OrderSide`
- New `OrderSide` enum (`BUY`, `SELL`) in `stocktrading.messaging`
- Updated publish call in the Stock Trading service that populates both new fields

**Acceptance criteria:**
- [ ] `OrderFilledEvent` data class has `idempotencyKey: UUID` and `side: OrderSide` fields
- [ ] `OrderSide` enum exists in `stocktrading.messaging` with values `BUY` and `SELL`
- [ ] The publish call passes `order.idempotencyKey` and `OrderSide.BUY`
- [ ] Existing `OrderFilledEvent` listener/consumer tests still pass (fields are additive)
- [ ] Unit test: `OrderFilledEvent` is constructed with all fields populated correctly

**Depends on:** none

---

## Layer: API-LEDGER — Ledger api/ interface extension

### API-LEDGER-1 — Add getBalance method to LedgerApi

**Layer:** Service (Ledger domain api/ interface)
**Domain:** ledger
**Use case:** view-portfolio
**Implements:** view-portfolio flow B — step 5
**Inputs:**
- `accountId: UUID`

**Outputs:**
- New method on `LedgerApi` interface in `ledger.api`: `getBalance(accountId: UUID): AccountBalanceResult`
- New `AccountBalanceResult` data class in `ledger.api`: `balance: BigDecimal`, `currency: String`
- Implementation in the Ledger service that reads `Account.balance` and `Account.currency` by `accountId`

**Acceptance criteria:**
- [ ] `LedgerApi` interface in `ledger.api` declares `getBalance(accountId: UUID): AccountBalanceResult`
- [ ] `AccountBalanceResult` data class exists in `ledger.api` with `balance: BigDecimal` and `currency: String`
- [ ] Implementation returns the correct balance and currency for a valid `accountId`
- [ ] Returns a typed domain exception (e.g. `AccountNotFoundException`) if `accountId` does not resolve
- [ ] Unit test: happy path — returns correct balance and currency
- [ ] Unit test: account not found — throws domain exception

**Depends on:** none

---

## Layer: API-MARKETDATA — Market Data api/ interface extension

### API-MARKETDATA-1 — Add getPrices bulk method to MarketDataApi

**Layer:** Service (Market Data domain api/ interface)
**Domain:** marketdata
**Use case:** view-portfolio
**Implements:** view-portfolio flow B — step 4
**Inputs:**
- `tickers: List<String>`

**Outputs:**
- New method on `MarketDataApi` interface in `marketdata.api`: `getPrices(tickers: List<String>): Map<String, BigDecimal>`
- Implementation in the Market Data service that reads the in-memory `MarketDataSnapshot` cache for each requested ticker and returns a map of `ticker → currentPrice`
- Tickers not found in the cache are omitted from the result (no error thrown)

**Acceptance criteria:**
- [ ] `MarketDataApi` interface in `marketdata.api` declares `getPrices(tickers: List<String>): Map<String, BigDecimal>`
- [ ] Implementation reads from the existing in-memory `MarketDataSnapshot` cache
- [ ] Returns an empty map when the input list is empty
- [ ] Tickers not present in the cache are absent from the result map (no exception thrown)
- [ ] Unit test: all tickers present in cache — returns correct prices
- [ ] Unit test: some tickers missing from cache — only present tickers returned
- [ ] Unit test: empty ticker list — returns empty map

**Depends on:** none

---

## Layer: DB — Database

### DB-1 — Create Position JPA entity

**Layer:** Database
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** domain/model/position — all fields; aggregate-stock-position flow A steps 6a, 6b
**Inputs:**
- `domain/model/position.md` — all field definitions, types, and constraints
- Backend standards entity template

**Outputs:**
- `org.dpp.tradelab.portfolio.model.Position` JPA entity class
- `org.dpp.tradelab.portfolio.model.AssetType` enum (`STOCK`, `CRYPTO`)

**Acceptance criteria:**
- [ ] `Position` is annotated `@Entity`, is a plain `class` (not `data class`), implements `Persistable<UUID>`
- [ ] Fields: `id: UUID`, `userId: UUID`, `accountId: UUID`, `ticker: String`, `assetType: AssetType`, `quantity: BigDecimal`, `totalCost: BigDecimal`, `avgPrice: BigDecimal`, `minPrice: BigDecimal`, `maxPrice: BigDecimal`, `lastUpdated: Instant`
- [ ] `@Column(precision=19, scale=4)` on all `BigDecimal` fields
- [ ] `@Enumerated(EnumType.STRING)` on `assetType`
- [ ] Unique constraint on `(userId, accountId, ticker)` via `@Table(uniqueConstraints = [...])`
- [ ] `equals` and `hashCode` based on `id` only; `toString` implemented
- [ ] `_isNew: Boolean = true` `@Transient` flag; `isNew()` returns it
- [ ] No `@GeneratedValue` — id pre-assigned by service layer

**Depends on:** none

### DB-2 — Create ProcessedIdempotencyKey JPA entity

**Layer:** Database
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** aggregate-stock-position flow A — steps 3, 4; domain/model/position business rules (idempotency)
**Inputs:**
- `domain/model/position.md` — idempotency log description
- `domain/flows/aggregate-stock-position.md` flow A steps 3–4
- Backend standards entity template

**Outputs:**
- `org.dpp.tradelab.portfolio.model.ProcessedIdempotencyKey` JPA entity class

> **Rationale:** This is a lean idempotency-guard table. It stores only the keys of events the Portfolio domain has already processed — no event payload data. Before any position update the service checks this table; if the key is present the event is discarded; if absent the key is inserted and the position update proceeds, both within the same transaction. This is intentionally minimal: the only columns are `id` (surrogate PK) and `idempotencyKey` (the deduplification key itself) and `processedAt` (for operational visibility). No event data is stored here.

**Acceptance criteria:**
- [ ] `ProcessedIdempotencyKey` is annotated `@Entity`, plain `class`, implements `Persistable<UUID>`
- [ ] Fields: `id: UUID`, `idempotencyKey: UUID` (unique constraint), `processedAt: Instant`
- [ ] `@Column(nullable=false, unique=true)` on `idempotencyKey`
- [ ] `@Column(nullable=false, updatable=false)` on `processedAt`
- [ ] `equals` and `hashCode` based on `id` only; `toString` implemented
- [ ] `_isNew` flag and `isNew()` per backend standards
- [ ] No event payload fields on this entity — it records only the key, nothing else

**Depends on:** none

---

## Layer: REPO — Repository

### REPO-1 — Create PositionRepository

**Layer:** Repository
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** aggregate-stock-position flow A steps 5, 6a, 6b; view-portfolio flow B step 3
**Inputs:**
- `Position` entity (DB-1)

**Outputs:**
- `org.dpp.tradelab.portfolio.repository.PositionRepository` Spring Data JPA interface

**Acceptance criteria:**
- [ ] Annotated `@Repository`
- [ ] Extends `JpaRepository<Position, UUID>`
- [ ] Custom method: `findByUserIdAndAccountIdAndTicker(userId: UUID, accountId: UUID, ticker: String): Optional<Position>`
- [ ] Custom method: `findAllByAccountIdAndQuantityGreaterThan(accountId: UUID, minQuantity: BigDecimal): List<Position>` — used by `SVC-2` to retrieve only active holdings (`quantity > 0`), correctly excluding any zero-quantity rows retained for audit after a future full sell-out
- [ ] No business logic in the interface
- [ ] Repository test for both custom query methods using embedded H2

**Depends on:** DB-1

### REPO-2 — Create ProcessedIdempotencyKeyRepository

**Layer:** Repository
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** aggregate-stock-position flow A steps 3, 4
**Inputs:**
- `ProcessedIdempotencyKey` entity (DB-2)

**Outputs:**
- `org.dpp.tradelab.portfolio.repository.ProcessedIdempotencyKeyRepository` Spring Data JPA interface

**Acceptance criteria:**
- [ ] Annotated `@Repository`
- [ ] Extends `JpaRepository<ProcessedIdempotencyKey, UUID>`
- [ ] Custom method: `existsByIdempotencyKey(idempotencyKey: UUID): Boolean`
- [ ] No business logic in the interface
- [ ] Repository test: `existsByIdempotencyKey` returns `true` when key is present, `false` when absent

**Depends on:** DB-2

---

## Layer: EXCEPTION — Exceptions

### EXCEPTION-1 — Create Portfolio domain exceptions

**Layer:** Exception
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** view-portfolio flow B error cases (account not found, account not owned, upstream failures)
**Inputs:**
- `domain/flows/view-portfolio.md` flow B error cases

**Outputs:**
- `org.dpp.tradelab.portfolio.exception.PortfolioAccountNotFoundException`
- `org.dpp.tradelab.portfolio.exception.PortfolioAccountAccessDeniedException`
- `org.dpp.tradelab.portfolio.exception.PortfolioPriceUnavailableException`
- `org.dpp.tradelab.portfolio.exception.PortfolioBalanceUnavailableException`
- `GlobalExceptionHandler` updated to map all four exceptions to their HTTP status codes

**Acceptance criteria:**
- [ ] Each exception extends `RuntimeException` with a single `message: String` constructor
- [ ] `GlobalExceptionHandler` maps `PortfolioAccountNotFoundException` → HTTP 404
- [ ] `GlobalExceptionHandler` maps `PortfolioAccountAccessDeniedException` → HTTP 403
- [ ] `GlobalExceptionHandler` maps `PortfolioPriceUnavailableException` → HTTP 502
- [ ] `GlobalExceptionHandler` maps `PortfolioBalanceUnavailableException` → HTTP 502
- [ ] All mappings use the standard error response shape: `{ status, error, details[] }`

**Depends on:** none

---

## Layer: SVC — Service

### SVC-1 — Create PortfolioPositionService: handleOrderFilled

**Layer:** Service
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** aggregate-stock-position flow A — steps 2–7
**Inputs:**
- `OrderFilledEvent` (extended: `orderId`, `accountId`, `userId`, `ticker`, `quantity`, `executionPrice`, `idempotencyKey`, `side`, `timestamp`) — EVT-1
- `PositionRepository` (REPO-1)
- `ProcessedIdempotencyKeyRepository` (REPO-2)

**Outputs:**
- `org.dpp.tradelab.portfolio.service.PortfolioPositionService` with method `handleOrderFilled(event: OrderFilledEvent)`

**Acceptance criteria:**
- [ ] Method is `@Transactional`
- [ ] Checks `existsByIdempotencyKey(event.idempotencyKey)` — if `true`, returns immediately (no-op, no writes)
- [ ] Inserts a new `ProcessedIdempotencyKey` row (with `idempotencyKey` and `processedAt = Instant.now()`) within the same transaction as the position update
- [ ] Queries for existing `Position` by `userId + accountId + ticker`
- [ ] New position (no row exists): creates `Position` with `quantity = event.quantity`, `totalCost = event.quantity × event.executionPrice`, `avgPrice = event.executionPrice`, `minPrice = event.executionPrice`, `maxPrice = event.executionPrice`, `lastUpdated = event.timestamp`; `id` pre-assigned via `UUID.randomUUID()`
- [ ] Existing position: increments `quantity`; increments `totalCost`; recalculates `avgPrice = totalCost / quantity`; updates `minPrice = min(minPrice, executionPrice)`; updates `maxPrice = max(maxPrice, executionPrice)`; sets `lastUpdated`
- [ ] All `BigDecimal` arithmetic uses `BigDecimal` operations — no floating-point
- [ ] Unit test: duplicate `idempotencyKey` — no position write, returns immediately
- [ ] Unit test: new position (BUY, no existing row) — position created with all fields correct
- [ ] Unit test: existing position (BUY, row exists) — quantity, totalCost, avgPrice, minPrice, maxPrice all updated correctly
- [ ] Unit test: transaction rollback — if position save fails, `ProcessedIdempotencyKey` insert also rolls back

**Depends on:** DB-1, DB-2, REPO-1, REPO-2, EVT-1

### SVC-2 — Create PortfolioQueryService: getHoldings

**Layer:** Service
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** view-portfolio flow B — steps 2–7
**Inputs:**
- `accountId: UUID`, `userId: UUID`
- `PositionRepository` (REPO-1)
- `MarketDataApi` (API-MARKETDATA-1)
- `LedgerApi` (API-LEDGER-1)

**Outputs:**
- `org.dpp.tradelab.portfolio.service.PortfolioQueryService` with method `getHoldings(accountId: UUID, userId: UUID): PortfolioHoldingsResult`
- Internal data classes: `PortfolioHoldingsResult`, `StockHoldingResult`, `CashHoldingResult`

**Acceptance criteria:**
- [ ] Method is `@Transactional(readOnly = true)`
- [ ] Validates account ownership using `LedgerApi.getBalance` — throws `PortfolioAccountNotFoundException` if account not found; throws `PortfolioAccountAccessDeniedException` if `userId` does not match
- [ ] Queries positions with `quantity > 0` for the account via `PositionRepository.findAllByAccountIdAndQuantityGreaterThan`
- [ ] If positions are non-empty, calls `MarketDataApi.getPrices(tickers)` — wraps any exception in `PortfolioPriceUnavailableException`
- [ ] Calls `LedgerApi.getBalance(accountId)` — wraps any exception in `PortfolioBalanceUnavailableException`
- [ ] Computes `currentValue = quantity × currentPrice` per stock; `totalValue = sum(currentValue) + cash balance`; `portfolioPercent = currentValue / totalValue × 100` (returns `null` when `totalValue = 0`); `unrealisedPnL = (currentPrice − avgPrice) × quantity`
- [ ] All `BigDecimal` arithmetic — no floating-point
- [ ] Unit test: happy path — correct enriched response
- [ ] Unit test: account not owned by user — throws `PortfolioAccountAccessDeniedException`
- [ ] Unit test: account not found — throws `PortfolioAccountNotFoundException`
- [ ] Unit test: Market Data call fails — throws `PortfolioPriceUnavailableException`
- [ ] Unit test: Ledger call fails — throws `PortfolioBalanceUnavailableException`
- [ ] Unit test: no stock positions — empty holdings list, cash row only
- [ ] Unit test: `totalValue = 0` — all `portfolioPercent` fields are `null`

**Depends on:** REPO-1, EXCEPTION-1, API-LEDGER-1, API-MARKETDATA-1

---

## Layer: CONTROLLER — Controller

### CONTROLLER-1 — Implement PortfolioApiDelegateImpl: getHoldings

**Layer:** Controller
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** view-portfolio flow B — steps 1, 8
**Inputs:**
- Generated `PortfolioApiDelegate` interface (from API-CONTRACT-1)
- `PortfolioQueryService.getHoldings` (SVC-2)
- `accountId: UUID` from query parameter
- `userId: UUID` from query parameter

**Outputs:**
- `org.dpp.tradelab.portfolio.controller.PortfolioApiDelegateImpl` implementing generated `PortfolioApiDelegate`

**Acceptance criteria:**
- [ ] Annotated `@Service`, implements generated `PortfolioApiDelegate`
- [ ] `getHoldings` extracts `accountId` and `userId` from query params; delegates entirely to `PortfolioQueryService.getHoldings`
- [ ] Returns `ResponseEntity<PortfolioHoldingsResponse>` HTTP 200 on success
- [ ] No business logic in the controller
- [ ] `@SpringBootTest` + MockMvc test: HTTP 200 with correct response body on happy path
- [ ] `@SpringBootTest` + MockMvc test: HTTP 403 when `PortfolioAccountAccessDeniedException` thrown
- [ ] `@SpringBootTest` + MockMvc test: HTTP 404 when `PortfolioAccountNotFoundException` thrown
- [ ] `@SpringBootTest` + MockMvc test: HTTP 502 when `PortfolioPriceUnavailableException` thrown
- [ ] `@SpringBootTest` + MockMvc test: HTTP 502 when `PortfolioBalanceUnavailableException` thrown

**Depends on:** SVC-2, EXCEPTION-1, API-CONTRACT-1

---

## Layer: EVT — Portfolio event listener

### EVT-2 — Create StockTradingEventListener in portfolio.messaging

**Layer:** Event
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** aggregate-stock-position flow A — step 1
**Inputs:**
- `OrderFilledEvent` (extended — EVT-1)
- `PortfolioPositionService.handleOrderFilled` (SVC-1)

**Outputs:**
- `org.dpp.tradelab.portfolio.messaging.StockTradingEventListener` — `@Component` listener class

**Acceptance criteria:**
- [ ] Annotated `@Component` (not `@Service`)
- [ ] Single listener method `onOrderFilled(event: OrderFilledEvent)` annotated `@TransactionalEventListener(phase = AFTER_COMMIT)`
- [ ] Listener method body contains exactly one call: `portfolioPositionService.handleOrderFilled(event)` — no business logic
- [ ] Constructor injection of `PortfolioPositionService`
- [ ] Unit test: `onOrderFilled` calls `handleOrderFilled` with the exact event payload

**Depends on:** SVC-1, EVT-1

---

## Layer: API-CONTRACT — OpenAPI contract

### API-CONTRACT-1 — Create portfolio-openapi.yaml

**Layer:** API Contract
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** view-portfolio flow B — steps 1, 8 (HTTP surface)
**Inputs:**
- Path: `GET /portfolio/holdings`
- Query parameters: `accountId` (UUID, required), `userId` (UUID, required)
- Success response schema: `PortfolioHoldingsResponse` containing `holdings: StockHolding[]` and `cash: CashHolding`
- `StockHolding` fields: `ticker`, `quantity`, `currentPrice`, `currentValue`, `minPrice`, `maxPrice`, `avgPrice`, `portfolioPercent` (nullable), `unrealisedPnL`
- `CashHolding` fields: `balance`, `currency`, `portfolioPercent` (nullable)
- Error responses: 401, 403, 404, 502 — all using `ErrorResponse` schema

**Outputs:**
- `services/contract/portfolio-openapi.yaml` — new file

**Acceptance criteria:**
- [ ] `info.title` is `Trade Lab API — Portfolio`
- [ ] `GET /portfolio/holdings` defined with `operationId: getHoldings`
- [ ] `accountId` and `userId` query parameters defined as required UUIDs
- [ ] `PortfolioHoldingsResponse` schema defined with `holdings` array and `cash` object
- [ ] `StockHolding` schema includes all nine fields; `portfolioPercent` marked nullable
- [ ] `CashHolding` schema includes `balance`, `currency`, `portfolioPercent` (nullable)
- [ ] All monetary and quantity fields use `type: number`
- [ ] 401, 403, 404, 502 error responses defined using `ErrorResponse` schema
- [ ] `ErrorResponse` schema matches the shape used in other domain contracts: `{ status: integer, error: string, details: string[] }`
- [ ] YAML is valid OpenAPI 3.0.3

**Depends on:** none

---

## Layer: CLI — API Client

### CLI-1 — Create portfolio API client: fetchPortfolioHoldings

**Layer:** API Client
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** view-portfolio flow B — step 1 (frontend HTTP call)
**Inputs:**
- `GET /api/v1/portfolio/holdings?accountId={accountId}&userId={userId}` (API-CONTRACT-1)
- Shared Axios instance from `shared/api/`
- TypeScript types derived from `portfolio-openapi.yaml`

**Outputs:**
- `services/front-end/src/domains/portfolio/api/portfolioApi.ts`
- `services/front-end/src/domains/portfolio/types/portfolio.types.ts` — `PortfolioHoldingsResponse`, `StockHolding`, `CashHolding` interfaces
- Exported constant `PORTFOLIO_HOLDINGS_KEY`

**Acceptance criteria:**
- [ ] `fetchPortfolioHoldings(accountId: string, userId: string): Promise<PortfolioHoldingsResponse>` defined
- [ ] Uses shared Axios instance — no new Axios instance created
- [ ] `PortfolioHoldingsResponse`, `StockHolding`, `CashHolding` TypeScript interfaces match the OpenAPI schema exactly (all fields, correct nullability)
- [ ] `PORTFOLIO_HOLDINGS_KEY` exported as a constant
- [ ] No `any` types
- [ ] Unit test (vi.mock): success path returns typed `PortfolioHoldingsResponse`
- [ ] Unit test: Axios error propagates correctly

**Depends on:** API-CONTRACT-1

---

## Layer: STATE — State management

### STATE-1 — Create portfolio Zustand slice (account selector state)

**Layer:** State
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** view-portfolio flow A step 4, flow C step 2
**Inputs:**
- Selected `accountId: string | null` (client state — not server state)

**Outputs:**
- `services/front-end/src/domains/portfolio/hooks/usePortfolioStore.ts`
- Slice shape: `{ selectedAccountId: string | null; setSelectedAccountId: (id: string) => void }`

**Acceptance criteria:**
- [ ] Slice defined using Zustand `create`
- [ ] `selectedAccountId` initialises to `null`
- [ ] `setSelectedAccountId` updates `selectedAccountId`
- [ ] No server state stored in this slice
- [ ] Unit test: initial state is `null`
- [ ] Unit test: `setSelectedAccountId` updates the value correctly

**Depends on:** none

### STATE-2 — Create usePortfolioHoldings TanStack Query hook

**Layer:** State
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** view-portfolio flow B — step 1 (frontend fetch), flow C step 3 (re-fetch on account switch)
**Inputs:**
- `fetchPortfolioHoldings` from CLI-1
- `PORTFOLIO_HOLDINGS_KEY` from CLI-1
- `accountId: string | null` from STATE-1
- `userId: string` from session Zustand slice

**Outputs:**
- `services/front-end/src/domains/portfolio/hooks/usePortfolioHoldings.ts`

**Acceptance criteria:**
- [ ] Uses `useQuery` from TanStack Query v5
- [ ] `queryKey` includes `PORTFOLIO_HOLDINGS_KEY`, `accountId`, and `userId`
- [ ] `queryFn` calls `fetchPortfolioHoldings(accountId, userId)`
- [ ] Query is disabled (`enabled: false`) when `accountId` is `null`
- [ ] Returns `{ data, isLoading, isError, error }` — no business logic in the hook
- [ ] Unit test (`renderHook`): query disabled when `accountId` is `null`
- [ ] Unit test: happy path — returns `PortfolioHoldingsResponse` data
- [ ] Unit test: error state — `isError` is true when API call fails

**Depends on:** CLI-1, STATE-1

---

## Layer: COMP — Components

### COMP-1 — Create PortfolioAccountSelector component

**Layer:** Component
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** view-portfolio flow A steps 3–4, flow C steps 1–2
**Inputs:**
- `accounts: AccountResponse[]` — list of active accounts (from existing Ledger TanStack Query)
- `selectedAccountId: string | null`
- `onAccountChange: (accountId: string) => void`

**Outputs:**
- `services/front-end/src/domains/portfolio/components/PortfolioAccountSelector.tsx`
- `PortfolioAccountSelectorProps` interface

**Acceptance criteria:**
- [ ] Renders a dropdown listing active accounts; each option shows `name` and `currency`
- [ ] Accounts ordered by `createdAt` ascending (order preserved from API response)
- [ ] Currently selected account is the active option
- [ ] Calls `onAccountChange` with the selected `accountId` on user selection change
- [ ] Renders empty-state message "No accounts available. Open an account first." when `accounts` is empty
- [ ] No API calls or store access directly in the component
- [ ] Unit test: renders account options correctly
- [ ] Unit test: calls `onAccountChange` with correct `accountId` on selection change
- [ ] Unit test: renders empty-state message when accounts list is empty

**Depends on:** none

### COMP-2 — Create PortfolioHoldingsTable component

**Layer:** Component
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** view-portfolio flow B step 9, flow D steps 2–3; portfolio table specification in `view-portfolio.md`
**Inputs:**
- `holdings: StockHolding[]`
- `cash: CashHolding`
- `currency: string` — account base currency for display formatting

**Outputs:**
- `services/front-end/src/domains/portfolio/components/PortfolioHoldingsTable.tsx`
- `PortfolioHoldingsTableProps` interface

**Acceptance criteria:**
- [ ] Renders one row per `StockHolding` plus one pinned cash row at the bottom
- [ ] Columns: Ticker, Shares, Current Value, Min Share Price, Max Share Price, Avg Bought Price, % of Portfolio, Unrealised P&L
- [ ] Cash row: Ticker = account `currency` (e.g. `USD`), Shares = `—`, Min/Max/Avg/Unrealised P&L = `—`, Current Value = `cash.balance`, % of Portfolio = `cash.portfolioPercent ?? '—'`
- [ ] All monetary values displayed to 2 decimal places
- [ ] `portfolioPercent = null` renders as `—`
- [ ] Default sort: `ticker` ascending on initial render
- [ ] Clicking a column header cycles: ascending → descending → default (ticker asc); sort indicator arrow shown on active column
- [ ] Cash row is always pinned at the bottom regardless of active sort
- [ ] Positive `unrealisedPnL` rendered with green styling, negative with red styling
- [ ] Empty `holdings` array renders cash row only with no error
- [ ] No API calls or store access directly in the component
- [ ] Unit test: all columns render correct values
- [ ] Unit test: cash row is always last regardless of sort
- [ ] Unit test: clicking a column header sorts stock rows; cash row stays last
- [ ] Unit test: `portfolioPercent = null` renders as `—`
- [ ] Unit test: positive P&L has green class, negative has red class

**Depends on:** CLI-1

---

## Layer: SCREEN — Screen

### SCREEN-1 — Create PortfolioPage and register /portfolio route

**Layer:** Screen
**Domain:** portfolio
**Use case:** view-portfolio
**Implements:** view-portfolio flows A, B, C, D — full page composition; use case screen spec (route `/portfolio`)
**Inputs:**
- `PortfolioAccountSelector` (COMP-1)
- `PortfolioHoldingsTable` (COMP-2)
- `usePortfolioHoldings` hook (STATE-2)
- `usePortfolioStore` slice (STATE-1)
- Existing `listAccounts` TanStack Query hook (Ledger domain — already implemented)
- Session Zustand slice (`userId`)

**Outputs:**
- `services/front-end/src/domains/portfolio/pages/PortfolioPage.tsx`
- Route `/portfolio` registered in `app/router.tsx`
- "Portfolio" nav link added to the sidebar alongside existing nav links

**Acceptance criteria:**
- [ ] Route `/portfolio` renders `PortfolioPage`
- [ ] On mount: fetches active accounts; applies default account selection (first account if no prior selection in slice; preserves existing selection if set)
- [ ] Renders `PortfolioAccountSelector` with account list and current selection
- [ ] Account selection change: updates slice via `setSelectedAccountId`; triggers re-fetch via `usePortfolioHoldings`
- [ ] While holdings loading: renders loading state (spinner or skeleton)
- [ ] On holdings success: renders `PortfolioHoldingsTable` with `holdings` and `cash` from response
- [ ] On holdings error — price data unavailable (502): renders "Could not load portfolio. Price data unavailable."
- [ ] On holdings error — balance data unavailable (502): renders "Could not load portfolio. Balance data unavailable."
- [ ] On account fetch error: selector shows "Could not load accounts."
- [ ] No active accounts: selector shows "No accounts available. Open an account first."; table not rendered
- [ ] No stock positions: table renders cash row only — no error
- [ ] Unauthenticated (HTTP 401): redirects to `/login`
- [ ] "Portfolio" nav link visible in sidebar when session is active
- [ ] Page contains no business logic — all logic in hooks and components
- [ ] Unit test: renders loading state while fetching
- [ ] Unit test: renders `PortfolioHoldingsTable` on successful fetch
- [ ] Unit test: renders price unavailable error message on 502 price error
- [ ] Unit test: renders balance unavailable error message on 502 balance error
- [ ] Unit test: renders empty-state for no accounts
- [ ] Unit test: account selection change triggers re-fetch

**Depends on:** COMP-1, COMP-2, STATE-1, STATE-2

---

## Dependency summary

| Task ID | Title | Depends on |
|---|---|---|
| EVT-1 | Extend OrderFilledEvent (stocktrading) | none |
| API-LEDGER-1 | Add getBalance to LedgerApi | none |
| API-MARKETDATA-1 | Add getPrices to MarketDataApi | none |
| DB-1 | Create Position JPA entity | none |
| DB-2 | Create ProcessedIdempotencyKey JPA entity | none |
| REPO-1 | Create PositionRepository | DB-1 |
| REPO-2 | Create ProcessedIdempotencyKeyRepository | DB-2 |
| EXCEPTION-1 | Create Portfolio domain exceptions | none |
| SVC-1 | PortfolioPositionService: handleOrderFilled | DB-1, DB-2, REPO-1, REPO-2, EVT-1 |
| SVC-2 | PortfolioQueryService: getHoldings | REPO-1, EXCEPTION-1, API-LEDGER-1, API-MARKETDATA-1 |
| API-CONTRACT-1 | Create portfolio-openapi.yaml | none |
| CONTROLLER-1 | PortfolioApiDelegateImpl: getHoldings | SVC-2, EXCEPTION-1, API-CONTRACT-1 |
| EVT-2 | StockTradingEventListener in portfolio.messaging | SVC-1, EVT-1 |
| CLI-1 | portfolio API client | API-CONTRACT-1 |
| STATE-1 | portfolio Zustand slice | none |
| STATE-2 | usePortfolioHoldings TanStack Query hook | CLI-1, STATE-1 |
| COMP-1 | PortfolioAccountSelector component | none |
| COMP-2 | PortfolioHoldingsTable component | CLI-1 |
| SCREEN-1 | PortfolioPage and /portfolio route | COMP-1, COMP-2, STATE-1, STATE-2 |
