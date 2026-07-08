# Tasks: Buy Stock at Market Price

**Use case:** `trade-stock-page` (buy-stock extension)
**Issue:** #37
**Domain docs:**
- `domain/model/order.md`
- `domain/model/ledger-entry.md`
- `domain/flows/buy-stock.md`
- `domain/usecases/trade-stock-page.md`

---

## Files Read During Decomposition

- `AGENTS.md`
- `domain/model/account.md`
- `domain/model/asset-subscription.md`
- `domain/model/ledger-entry.md` _(updated — amount for stock entries = share quantity)_
- `domain/model/market-data-snapshot.md`
- `domain/model/order.md` _(new)_
- `domain/model/session.md`
- `domain/model/user.md`
- `domain/flows/buy-stock.md` _(new)_
- `domain/flows/account-top-up.md`
- `domain/flows/manage-asset-subscriptions.md`
- `domain/flows/market-data-websocket-feed.md`
- `domain/flows/select-trading-account.md`
- `domain/usecases/trade-stock-page.md` _(updated)_
- `standards/architecture.md`
- `standards/backend.md`
- `standards/frontend.md`
- `decisions/2026-07-08-ledger-entry-amount-stock-semantics.md`
- `decisions/2026-07-08-stock-trading-ledger-sync-api.md`

---

## Use Case Summary

An authenticated user on `/trade` right-clicks a ticker row in the market data grid and selects "Buy". A buy panel opens, capturing the current grid price as an indicative `priceSnapshot`. The user enters a quantity (fractional or whole) and confirms. The frontend submits `POST /api/v1/orders` with an `Idempotency-Key` header. The backend validates the request, reads the live execution price from the `MarketDataSnapshot` cache (which may differ from `priceSnapshot`), checks the selected account has sufficient funds, and atomically persists the `Order` plus two `LedgerEntry` rows (cash DEBIT + stock CREDIT) in a single transaction. The order settles synchronously as `FILLED` or `REJECTED`. The frontend shows the fill confirmation with `executionPrice`-based total cost and invalidates the account balance cache. Both outcomes are persisted.

**Models involved:** `Order` (new, stocktrading), `LedgerEntry` (ledger), `Account` (ledger), `MarketDataSnapshot` (marketdata), `Session` (frontend only).
**Events emitted:** `OrderFilledEvent`, `OrderRejectedEvent` (both from `stocktrading.messaging`).
**Cross-domain calls:** Stock Trading → Ledger via `LedgerApi` (sync); Stock Trading → Market Data via `MarketDataApi` (sync).

---

## Ambiguities and Gaps

None — all clarified during intake:
- `LedgerEntry.amount` for stock entries = share quantity (decision logged).
- `executionPrice` is re-read from the cache at fill time (not client-supplied `priceSnapshot`).
- Post-fill UI shows `executionPrice`-based cost, not `priceSnapshot`-based cost.
- `REJECTED` orders are persisted.
- Duplicate idempotency key → HTTP 409, client handles retry.
- Stock Trading calls Ledger synchronously within the same transaction (decision logged).

---

## Layer: DB

### DB-1 — Add `Order` JPA entity and enums to `stocktrading.model`

**Layer:** Database
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/model/order.md` — all properties and business rules; `domain/flows/buy-stock.md` Flow C step 5 (persist PENDING), steps 7 and 9 (update to REJECTED/FILLED)
**Inputs:**
- `domain/model/order.md` (full spec)
- `standards/backend.md` (entity template, UUID PK, `Persistable<UUID>`, `BigDecimal` for decimals, `EnumType.STRING`)
**Outputs:**
- `org.dpp.tradelab.stocktrading.model.Order` — JPA entity class
- `org.dpp.tradelab.stocktrading.model.OrderType` — enum: `MARKET`
- `org.dpp.tradelab.stocktrading.model.OrderStatus` — enum: `PENDING`, `FILLED`, `REJECTED`
**Acceptance criteria:**
- [ ] `Order` is annotated `@Entity`, `@Table(name = "orders")`, implements `Persistable<UUID>`
- [ ] `id`: `UUID`, `@Id`, `@Column(nullable = false, updatable = false)`, pre-assigned; `_isNew = true` pattern applied
- [ ] `idempotencyKey`: `UUID`, `@Column(nullable = false, unique = true, updatable = false)`
- [ ] `accountId`: `UUID`, `@Column(nullable = false, updatable = false)`
- [ ] `userId`: `UUID`, `@Column(nullable = false, updatable = false)`
- [ ] `ticker`: `String`, `@Column(nullable = false, updatable = false)`
- [ ] `quantity`: `BigDecimal`, `@Column(nullable = false, precision = 19, scale = 4, updatable = false)`
- [ ] `orderType`: `OrderType`, `@Enumerated(EnumType.STRING)`, `@Column(nullable = false, updatable = false)`
- [ ] `status`: `OrderStatus`, `@Enumerated(EnumType.STRING)`, `@Column(nullable = false)`
- [ ] `priceSnapshot`: `BigDecimal`, `@Column(nullable = false, precision = 19, scale = 4, updatable = false)`
- [ ] `executionPrice`: `BigDecimal?`, `@Column(precision = 19, scale = 4)`, nullable
- [ ] `rejectionReason`: `String?`, `@Column`, nullable
- [ ] `createdAt`: `@CreationTimestamp`
- [ ] `updatedAt`: `@UpdateTimestamp`
- [ ] `equals`/`hashCode` based on `id` only; `toString` implemented manually
- [ ] `OrderType` enum compiles with value `MARKET`
- [ ] `OrderStatus` enum compiles with values `PENDING`, `FILLED`, `REJECTED`
- [ ] Unit test: entity can be constructed with all required fields; `isNew()` returns `true` on fresh instance
**Depends on:** none

---

## Layer: REPO

### REPO-1 — Add `OrderRepository` to `stocktrading.repository`

**Layer:** Repository
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/model/order.md` — idempotency key unique constraint; `domain/flows/buy-stock.md` Flow C step 5 (persist order)
**Inputs:**
- `org.dpp.tradelab.stocktrading.model.Order` (from DB-1)
- `standards/backend.md` (Spring Data JPA interface conventions)
**Outputs:**
- `org.dpp.tradelab.stocktrading.repository.OrderRepository` — Spring Data JPA interface extending `JpaRepository<Order, UUID>`; one custom finder: `existsByIdempotencyKey(idempotencyKey: UUID): Boolean`
**Acceptance criteria:**
- [ ] Interface extends `JpaRepository<Order, UUID>`
- [ ] `existsByIdempotencyKey(idempotencyKey: UUID): Boolean` method declared (Spring Data derived query — no `@Query`)
- [ ] Repository test (`@SpringBootTest` + `@AutoConfigureTestEntityManager` + `@Transactional`): saving two orders with the same `idempotencyKey` throws a constraint violation
- [ ] Repository test: `existsByIdempotencyKey` returns `true` for a persisted key and `false` for an unknown key
**Depends on:** DB-1

---

## Layer: EXCEPTION

### EXCEPTION-1 — Add domain exception classes to `stocktrading.exception`

**Layer:** Exception
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C error cases: insufficient funds, ticker not found, duplicate idempotency key, account not found, account not owned, account not active
**Inputs:**
- `domain/flows/buy-stock.md` Flow C error cases
- `standards/backend.md` (typed domain exceptions, `GlobalExceptionHandler`)
**Outputs:**
- `org.dpp.tradelab.stocktrading.exception.InsufficientFundsException` — runtime exception; carries `accountId: UUID`, `required: BigDecimal`, `available: BigDecimal`
- `org.dpp.tradelab.stocktrading.exception.TickerNotFoundException` — runtime exception; carries `ticker: String`
- `org.dpp.tradelab.stocktrading.exception.DuplicateIdempotencyKeyException` — runtime exception; carries `idempotencyKey: UUID`
- `org.dpp.tradelab.stocktrading.exception.OrderAccountNotFoundException` — runtime exception; carries `accountId: UUID`
- `org.dpp.tradelab.stocktrading.exception.OrderAccountNotOwnedException` — runtime exception; carries `accountId: UUID`, `userId: UUID`
- `org.dpp.tradelab.stocktrading.exception.OrderAccountNotActiveException` — runtime exception; carries `accountId: UUID`
**Acceptance criteria:**
- [ ] All six classes are plain Kotlin classes extending `RuntimeException`
- [ ] Each carries the fields listed above, accessible via constructor parameters
- [ ] `GlobalExceptionHandler` maps: `InsufficientFundsException` → HTTP 200 body with `status: REJECTED` and `rejectionReason`; `TickerNotFoundException` → HTTP 400; `DuplicateIdempotencyKeyException` → HTTP 409; `OrderAccountNotFoundException` → HTTP 404; `OrderAccountNotOwnedException` + `OrderAccountNotActiveException` → HTTP 403
- [ ] No business logic inside exception classes
**Depends on:** none

---

## Layer: EVT

### EVT-1 — Add `OrderFilledEvent` and `OrderRejectedEvent` data classes to `stocktrading.messaging`

**Layer:** Event
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/model/order.md` Events; `domain/flows/buy-stock.md` Flow C step 9 (`OrderFilledEvent`), step 7 rejection path (`OrderRejectedEvent`)
**Inputs:**
- `domain/model/order.md` — event payloads
- `standards/backend.md` — event naming `{Entity}{Action}Event`, data class, payload fields
**Outputs:**
- `org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent` — `data class` with fields: `orderId: UUID`, `accountId: UUID`, `userId: UUID`, `ticker: String`, `quantity: BigDecimal`, `executionPrice: BigDecimal`, `timestamp: Instant`
- `org.dpp.tradelab.stocktrading.messaging.OrderRejectedEvent` — `data class` with fields: `orderId: UUID`, `accountId: UUID`, `userId: UUID`, `ticker: String`, `quantity: BigDecimal`, `rejectionReason: String`, `timestamp: Instant`
**Acceptance criteria:**
- [ ] Both are plain Kotlin `data class` types (no Spring annotations)
- [ ] All fields match the payloads specified in `domain/model/order.md`
- [ ] No JPA entity references in the payload — IDs only
- [ ] Files located in `org.dpp.tradelab.stocktrading.messaging`
**Depends on:** none

---

## Layer: SVC (Ledger — cross-domain api/)

### SVC-1 — Add `LedgerApi` interface to `ledger.api`

**Layer:** Service
**Domain:** ledger
**Use case:** buy-stock
**Implements:** `decisions/2026-07-08-stock-trading-ledger-sync-api.md`; `domain/flows/buy-stock.md` Flow C step 8 (write two ledger entries atomically)
**Inputs:**
- `domain/model/ledger-entry.md` — DEBIT/CASH and CREDIT/STOCK_BUY entry rules
- `domain/model/account.md` — balance update on DEBIT
- `standards/architecture.md` — cross-domain `api/` interface pattern
- `standards/backend.md` — no imports from `{domain}.model` across boundaries
**Outputs:**
- `org.dpp.tradelab.ledger.api.LedgerApi` — Kotlin interface with one method:
  `fun recordStockBuy(accountId: UUID, userId: UUID, ticker: String, quantity: BigDecimal, executionPrice: BigDecimal)`
**Acceptance criteria:**
- [ ] Interface is in `org.dpp.tradelab.ledger.api` — not in `service`, `model`, or any other sub-package
- [ ] Method signature matches the output spec exactly
- [ ] Interface has no Spring annotations (it is a plain Kotlin interface)
- [ ] No JPA entity types appear in the method signature — primitives and `UUID`/`BigDecimal` only
**Depends on:** none

---

### SVC-2 — Implement `LedgerApi.recordStockBuy` in `LedgerService`

**Layer:** Service
**Domain:** ledger
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 8 — write `DEBIT/CASH` entry (`amount = quantity × executionPrice`) and `CREDIT/STOCK_BUY` entry (`amount = quantity`); deduct cash from `Account.balance`
**Inputs:**
- `org.dpp.tradelab.ledger.api.LedgerApi` (from SVC-1)
- `org.dpp.tradelab.ledger.model.LedgerEntry` (existing JPA entity — must add `STOCK_BUY` support if not already present)
- `org.dpp.tradelab.ledger.model.Account` (existing JPA entity)
- `org.dpp.tradelab.ledger.repository.LedgerEntryRepository` (existing)
- `org.dpp.tradelab.ledger.repository.AccountRepository` (existing)
- `domain/model/ledger-entry.md` — business rules for stock entries
- `standards/backend.md` — `@Transactional` on write methods, no persistence in service (delegate to repo)
**Outputs:**
- `LedgerService` (existing class in `ledger.service`) implements `LedgerApi`; new `recordStockBuy` method added
**Acceptance criteria:**
- [ ] `LedgerService` implements `LedgerApi`
- [ ] Method is `@Transactional`
- [ ] Resolves account by `accountId`; throws `AccountNotFoundException` (existing ledger exception) if not found
- [ ] Checks `account.balance >= quantity × executionPrice`; throws `InsufficientFundsException` (from `stocktrading.exception`) — **wait**: LedgerService must not import from `stocktrading.exception`. The fund check belongs in the Stock Trading service (SVC-3), not here. `LedgerApi.recordStockBuy` is called only after the fund check passes. This method only performs the writes — it may throw a generic `IllegalStateException` if the balance is somehow insufficient as a safety net.
- [ ] Creates `LedgerEntry` with `type = DEBIT`, `assetType = CASH`, `amount = quantity × executionPrice` (BigDecimal multiplication, no rounding), `currency` = account base currency, `ticker = null`, `description = "Buy {ticker} x{quantity}"`
- [ ] Creates `LedgerEntry` with `type = CREDIT`, `assetType = STOCK_BUY`, `amount = quantity`, `currency` = account base currency, `ticker = ticker`, `description = "Buy {ticker} x{quantity}"`
- [ ] Deducts `quantity × executionPrice` from `account.balance` and saves the account
- [ ] Both entries and the account update are saved within the same `@Transactional` scope
- [ ] Unit tests cover: happy path (both entries saved, balance deducted), account not found throws exception
**Depends on:** SVC-1, DB-1 (Order entity not needed here, but LedgerEntry `STOCK_BUY` assetType must be present in existing entity)

---

## Layer: SVC (Market Data — cross-domain api/)

### SVC-3 — Add `MarketDataApi` interface to `marketdata.api`

**Layer:** Service
**Domain:** marketdata
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 6 — read current execution price from `MarketDataSnapshot` cache
**Inputs:**
- `domain/model/market-data-snapshot.md` — `currentPrice` field
- `standards/architecture.md` — cross-domain `api/` interface pattern
**Outputs:**
- `org.dpp.tradelab.marketdata.api.MarketDataApi` — Kotlin interface with one method:
  `fun getCurrentPrice(ticker: String): BigDecimal`
**Acceptance criteria:**
- [ ] Interface is in `org.dpp.tradelab.marketdata.api`
- [ ] Method throws (or returns null — document the contract) if the ticker has no cache entry. Given the flow guarantees the ticker is in the supported config and the cache is seeded at startup, a missing entry is a programming error. The method should throw `IllegalStateException` if the cache entry is absent.
- [ ] No Spring annotations on the interface
- [ ] No JPA entity types in the signature
**Depends on:** none

---

### SVC-4 — Implement `MarketDataApi.getCurrentPrice` in `MarketDataService`

**Layer:** Service
**Domain:** marketdata
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 6 — reads `MarketDataSnapshot.currentPrice` from in-memory cache for the requested ticker
**Inputs:**
- `org.dpp.tradelab.marketdata.api.MarketDataApi` (from SVC-3)
- Existing `MarketDataService` or equivalent service that owns the in-memory snapshot cache
- `domain/model/market-data-snapshot.md`
**Outputs:**
- Existing `MarketDataService` (in `marketdata.service`) implements `MarketDataApi`; new `getCurrentPrice` method added
**Acceptance criteria:**
- [ ] `MarketDataService` (or the service that owns the snapshot cache) implements `MarketDataApi`
- [ ] `getCurrentPrice(ticker)` reads from the in-memory cache and returns the `currentPrice` as `BigDecimal`
- [ ] Throws `IllegalStateException` with a clear message if the ticker has no cache entry
- [ ] Unit test: mock cache with a known ticker → correct price returned; unknown ticker → exception thrown
**Depends on:** SVC-3

---

## Layer: SVC (Stock Trading — core order placement)

### SVC-5 — Implement `StockTradingService.placeOrder` in `stocktrading.service`

**Layer:** Service
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C steps 4–10 (full order lifecycle: validate → PENDING → read execution price → check funds → write ledger → FILLED or REJECTED → emit event)
**Inputs:**
- `org.dpp.tradelab.stocktrading.model.Order`, `OrderType`, `OrderStatus` (from DB-1)
- `org.dpp.tradelab.stocktrading.repository.OrderRepository` (from REPO-1)
- `org.dpp.tradelab.stocktrading.exception.*` (from EXCEPTION-1)
- `org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent`, `OrderRejectedEvent` (from EVT-1)
- `org.dpp.tradelab.ledger.api.LedgerApi` (from SVC-1) — injected via constructor, import interface only
- `org.dpp.tradelab.marketdata.api.MarketDataApi` (from SVC-3) — injected via constructor, import interface only
- `org.dpp.tradelab.marketdata.api.MarketDataSupportedTickersApi` or equivalent — to validate ticker exists in config (see note below)
- `ApplicationEventPublisher` — injected via constructor
- `standards/backend.md` — `@Transactional` on write, constructor injection, no cross-domain model imports
- `domain/flows/buy-stock.md` — Flow C full step sequence

> **Note on ticker validation:** The supported tickers config is owned by Market Data. Stock Trading must validate the ticker via the Market Data `api/` interface. If a `MarketDataSupportedTickersApi` or equivalent does not yet exist, it must be created as part of this task (one method: `fun isTickerSupported(ticker: String): Boolean`), following the same pattern as SVC-3/SVC-4.

**Method signature:**
```
fun placeOrder(
    idempotencyKey: UUID,
    accountId: UUID,
    userId: UUID,
    ticker: String,
    quantity: BigDecimal,
    orderType: OrderType,
    priceSnapshot: BigDecimal
): Order
```
**Outputs:**
- `org.dpp.tradelab.stocktrading.service.StockTradingService` — new `@Service` class with `placeOrder` method
- Returns the persisted `Order` (either FILLED or REJECTED)

**Acceptance criteria:**
- [ ] `StockTradingService` is annotated `@Service`, uses constructor injection only
- [ ] `placeOrder` is `@Transactional`
- [ ] Step 4 — validates: `quantity > 0` (throws `IllegalArgumentException` → mapped to HTTP 400 by `GlobalExceptionHandler`), `orderType == MARKET`, ticker supported (throws `TickerNotFoundException` → HTTP 400), account exists and belongs to `userId` (throws `OrderAccountNotFoundException` → HTTP 404, `OrderAccountNotOwnedException` → HTTP 403), account is active (throws `OrderAccountNotActiveException` → HTTP 403)
- [ ] Step 5 — checks `orderRepository.existsByIdempotencyKey(idempotencyKey)`; if true throws `DuplicateIdempotencyKeyException` → HTTP 409; otherwise creates and saves `Order` with `status = PENDING`
- [ ] Step 6 — calls `marketDataApi.getCurrentPrice(ticker)` to obtain `executionPrice`
- [ ] Step 7 — reads account balance via `LedgerApi` or an account lookup; if `balance < quantity × executionPrice`, updates order to `REJECTED`, sets `rejectionReason = "Insufficient funds"`, saves, emits `OrderRejectedEvent`, returns order (HTTP 200 with REJECTED body)
- [ ] Step 8 — calls `ledgerApi.recordStockBuy(accountId, userId, ticker, quantity, executionPrice)` — only reached when funds are sufficient
- [ ] Step 9 — updates order to `FILLED`, sets `executionPrice`, saves, emits `OrderFilledEvent`
- [ ] All BigDecimal arithmetic uses `BigDecimal` multiplication (`multiply`) — no `Double` arithmetic
- [ ] Unit tests cover: happy path (FILLED, both events checked), insufficient funds (REJECTED, no ledger call), duplicate idempotency key (409), ticker not found (400), account not found (404), account not active (403), account not owned (403)
**Depends on:** DB-1, REPO-1, EXCEPTION-1, EVT-1, SVC-1, SVC-2, SVC-3, SVC-4

---

## Layer: CONTROLLER

### CONTROLLER-1 — Implement `StocktradingApiDelegateImpl` in `stocktrading.controller`

**Layer:** Controller
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 3 (`POST /api/v1/orders`), steps 10 and 7 (HTTP 200 FILLED/REJECTED responses), all error case HTTP codes
**Inputs:**
- Generated `StocktradingApiDelegate` (from API-CONTRACT-1 after code generation)
- `org.dpp.tradelab.stocktrading.service.StockTradingService` (from SVC-5)
- `standards/backend.md` — delegate pattern, `@Service` annotation on impl, generated DTOs only
**Outputs:**
- `org.dpp.tradelab.stocktrading.controller.StocktradingApiDelegateImpl` — `@Service` implementing generated `StocktradingApiDelegate`
- Implements `placeOrder(idempotencyKey: UUID, placeOrderRequest: PlaceOrderRequest): ResponseEntity<PlaceOrderResponse>`
**Acceptance criteria:**
- [ ] Class is `@Service`, implements `StocktradingApiDelegate`
- [ ] Reads `Idempotency-Key` header from the request and passes it to `StockTradingService.placeOrder` as `idempotencyKey: UUID`
- [ ] Delegates entirely to `StockTradingService.placeOrder` — no business logic
- [ ] Maps returned `Order` to generated `PlaceOrderResponse` DTO
- [ ] FILLED response: HTTP 200, body includes `orderId`, `status = "FILLED"`, `ticker`, `quantity`, `executionPrice`, `totalCost` (`quantity × executionPrice`), `accountId`, `createdAt`
- [ ] REJECTED response: HTTP 200, body includes `orderId`, `status = "REJECTED"`, `ticker`, `quantity`, `rejectionReason`, `accountId`, `createdAt`; `executionPrice` and `totalCost` are absent/null
- [ ] `MockMvc` tests cover: FILLED (200 + body), REJECTED (200 + body), 400 invalid quantity, 400 bad ticker, 404 account not found, 403 account not owned, 403 account not active, 409 duplicate idempotency key, 401 unauthenticated
**Depends on:** SVC-5, API-CONTRACT-1

---

## Layer: API-CONTRACT

### API-CONTRACT-1 — Create `services/contract/stocktrading-openapi.yaml`

**Layer:** OpenAPI Contract
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 3 (`POST /api/v1/orders`), step 10 (FILLED response), step 7 rejection path (REJECTED response), all error cases
**Inputs:**
- `domain/flows/buy-stock.md` — request body fields, response fields, all HTTP status codes
- `domain/model/order.md` — field types, constraints
- `standards/backend.md` — OpenAPI conventions: `info.title = "Trade Lab API — Stocktrading"`, `kotlin-spring` generator, `delegatePattern = true`, base path `/api/v1`, plural nouns, Bean Validation via `x-constraints`/pattern/minLength
**Paths introduced:**
- `POST /api/v1/orders`
**Outputs:**
- `services/contract/stocktrading-openapi.yaml` — new file

**Contract spec:**

`POST /api/v1/orders`
- Request header: `Idempotency-Key` (string/UUID format, required)
- Request body (`PlaceOrderRequest`):
  - `accountId` (string/uuid, required)
  - `ticker` (string, required, minLength 1, maxLength 10)
  - `quantity` (number, required, exclusiveMinimum 0) — represented as string in YAML schema type `number` with `format: decimal`
  - `orderType` (string enum `["MARKET"]`, required)
  - `priceSnapshot` (number, required, exclusiveMinimum 0)
- Response `200 OK` (`PlaceOrderResponse`):
  - `orderId` (string/uuid)
  - `status` (string enum `["FILLED", "REJECTED"]`)
  - `ticker` (string)
  - `quantity` (number)
  - `executionPrice` (number, nullable — present only on FILLED)
  - `totalCost` (number, nullable — present only on FILLED; `quantity × executionPrice`)
  - `rejectionReason` (string, nullable — present only on REJECTED)
  - `accountId` (string/uuid)
  - `createdAt` (string/date-time)
- Error responses: 400, 401, 403, 404, 409, 500 — all use the standard `ErrorResponse` schema (`status`, `error`, `details`)

**Acceptance criteria:**
- [ ] File is valid OpenAPI 3.0.3
- [ ] `info.title` is `"Trade Lab API — Stocktrading"`
- [ ] `POST /api/v1/orders` path is present with the full request body, header, and response schemas as specified
- [ ] `Idempotency-Key` header is declared as required on the operation
- [ ] `quantity` and `priceSnapshot` have `exclusiveMinimum: 0` constraints
- [ ] `orderType` is restricted to enum `["MARKET"]`
- [ ] All error status codes (400, 401, 403, 404, 409, 500) are declared
- [ ] `ErrorResponse` schema is defined inline or as a reusable component
- [ ] Running `./gradlew openApiGenerate` (once configured) produces `StocktradingApiDelegate` and `StocktradingApiController` without errors
**Depends on:** none (can be written before all other tasks; other tasks depend on it)

---

## Layer: CLI

### CLI-1 — Add `placeOrder` Axios call to `stocktrading/api/`

**Layer:** API Client
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 3 — `POST /api/v1/orders`
**Inputs:**
- `services/contract/stocktrading-openapi.yaml` (API-CONTRACT-1) — endpoint URL, request/response shapes
- `standards/frontend.md` — shared Axios instance from `shared/api/`, typed request/response, function naming `verb + noun`
**Outputs:**
- `services/front-end/src/domains/stocktrading/api/ordersApi.ts` — exports:
  - `PlaceOrderRequest` interface: `{ accountId: string; ticker: string; quantity: string; orderType: 'MARKET'; priceSnapshot: string }`
  - `PlaceOrderResponse` interface: `{ orderId: string; status: 'FILLED' | 'REJECTED'; ticker: string; quantity: string; executionPrice: string | null; totalCost: string | null; rejectionReason: string | null; accountId: string; createdAt: string }`
  - `ORDERS_QUERY_KEY` constant
  - `placeOrder(idempotencyKey: string, request: PlaceOrderRequest): Promise<PlaceOrderResponse>` — uses shared Axios instance, sets `Idempotency-Key` header
**Acceptance criteria:**
- [ ] `placeOrder` uses the shared Axios instance from `shared/api/` — no new Axios instance
- [ ] `Idempotency-Key` header is set on every request
- [ ] `quantity` and `priceSnapshot` are sent as strings (to preserve decimal precision) — or as numbers, matching the contract exactly
- [ ] All types are explicit — no `any`
- [ ] Unit test: `placeOrder` called → Axios POST to `/api/v1/orders` with correct body and header; mocked response correctly typed
**Depends on:** API-CONTRACT-1

---

## Layer: STATE

### STATE-1 — Add `usePlaceOrder` mutation hook to `stocktrading/hooks/`

**Layer:** State
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 2 (loading state), step 3 (submit), step 11 (show confirmation), step 12 (invalidate account cache), Flow D (decline — no API call)
**Inputs:**
- `placeOrder` from CLI-1
- TanStack Query `useMutation`
- `standards/frontend.md` — TanStack Query owns all server state; on-success invalidates the account query key
- Existing `ACCOUNTS_QUERY_KEY` from the ledger/account API module (import from `ledger/api/` — no cross-domain store access, only key reference)
**Outputs:**
- `services/front-end/src/domains/stocktrading/hooks/usePlaceOrder.ts` — exports `usePlaceOrder()` hook returning `useMutation` result wrapping `placeOrder`; on success invalidates `ACCOUNTS_QUERY_KEY`
**Acceptance criteria:**
- [ ] Hook uses `useMutation` from TanStack Query
- [ ] On mutation success, calls `queryClient.invalidateQueries({ queryKey: ACCOUNTS_QUERY_KEY })` (or equivalent scoped key) so the account balance refreshes
- [ ] Mutation variables include `idempotencyKey: string` and `PlaceOrderRequest`
- [ ] No Zustand store access — this is server state
- [ ] Unit test (`renderHook`): mock `placeOrder` → on success, ACCOUNTS_QUERY_KEY invalidated; on error, error state accessible
**Depends on:** CLI-1

---

## Layer: COMP

### COMP-1 — Add grid row context menu with "Buy" option to `MarketDataGrid`

**Layer:** Component
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow A steps 1–3 — right-click row → show context menu with "Buy" → open buy panel
**Inputs:**
- Existing `MarketDataGrid` component in `stocktrading/components/`
- `domain/flows/buy-stock.md` Flow A
- `standards/frontend.md` — functional components, explicit props interfaces, no API calls in components
**Outputs:**
- `MarketDataGrid` extended: right-click on any row calls an `onBuy(ticker: string, companyName: string, priceSnapshot: string)` prop with the row's data
- Context menu rendered on right-click showing a single "Buy" option; clicking it invokes `onBuy`; clicking elsewhere dismisses the menu
**Acceptance criteria:**
- [ ] Right-clicking a row shows a context menu with exactly one item: "Buy"
- [ ] Clicking "Buy" invokes `onBuy(ticker, companyName, priceSnapshot)` where `priceSnapshot` is the `currentPrice` from that row at the moment of right-click
- [ ] Clicking anywhere outside the menu (or pressing Escape) dismisses it without invoking `onBuy`
- [ ] `onBuy` prop is declared in `MarketDataGridProps`
- [ ] No API calls inside this component
- [ ] Unit tests: right-click renders menu; clicking "Buy" invokes `onBuy` with correct args; outside click dismisses menu
**Depends on:** none (extends existing component)

---

### COMP-2 — Create `BuyPanel` component in `stocktrading/components/`

**Layer:** Component
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow A step 4, Flow B steps 1–3, Flow C steps 1–2 and 11, Flow D steps 1–2
**Inputs:**
- `usePlaceOrder` hook from STATE-1
- `domain/usecases/trade-stock-page.md` — Buy Panel Specification
- `domain/flows/buy-stock.md` Flows A, B, C, D
- `standards/frontend.md` — functional component, explicit props, no direct API calls
**Outputs:**
- `services/front-end/src/domains/stocktrading/components/BuyPanel.tsx`
- `BuyPanelProps`:
  ```
  {
    ticker: string
    companyName: string
    priceSnapshot: string   // captured at right-click time
    accountId: string
    onClose: () => void
  }
  ```
**Acceptance criteria:**
- [ ] On mount, generates a UUID `idempotencyKey` stored in component state (`useState`)
- [ ] Displays: ticker symbol, company name, order type field (value "MARKET", read-only), quantity input, "Estimated cost" label showing `quantity × priceSnapshot` in real time
- [ ] Quantity input: accepts positive decimals and positive whole numbers; shows inline error "Quantity must be greater than zero." if ≤ 0; shows inline error "Please enter a valid number." for non-numeric input; Confirm button disabled until input is valid
- [ ] Confirm button shows a green tick icon; Decline button shows a red cross icon
- [ ] On Confirm click: disables both buttons, shows loading indicator; calls `usePlaceOrder` mutation with `{ idempotencyKey, accountId, ticker, quantity, orderType: 'MARKET', priceSnapshot }`
- [ ] On FILLED response: replaces panel content with fill confirmation showing ticker, quantity, `executionPrice`, `totalCost`, green tick, "Order filled"
- [ ] On REJECTED response (HTTP 200, `status: 'REJECTED'`): shows rejection message "Order rejected: {rejectionReason}"
- [ ] On error response (non-200): shows generic error message; re-enables buttons; generates a new `idempotencyKey` in state (so retry is safe)
- [ ] On Decline click (at any stage): calls `onClose()` immediately; no API call is made
- [ ] No direct Axios or query calls — only through `usePlaceOrder`
- [ ] Unit tests: renders with correct initial state; estimated cost updates on quantity change; confirm calls mutation with correct args; fill confirmation shown on FILLED; rejection message shown on REJECTED; decline calls `onClose`
**Depends on:** STATE-1

---

## Layer: SCREEN

### SCREEN-1 — Wire `BuyPanel` and context menu into `TradeStockPage`

**Layer:** Screen
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/usecases/trade-stock-page.md` happy path steps 10–13 (open buy panel, enter quantity, confirm, decline); failure scenarios: no active accounts → Confirm blocked; buy errors displayed in panel
**Inputs:**
- `BuyPanel` component (COMP-2)
- `MarketDataGrid` with `onBuy` prop (COMP-1)
- Existing `TradeStockPage` in `stocktrading/pages/`
- `domain/usecases/trade-stock-page.md` — Buy Panel Specification; failure scenarios for buy
- `standards/frontend.md` — pages assemble components; no business logic in pages
**Outputs:**
- `TradeStockPage` updated: manages buy panel open/closed state and the selected ticker context; passes `onBuy` to `MarketDataGrid`; conditionally renders `BuyPanel` when open
**Acceptance criteria:**
- [ ] `TradeStockPage` holds `buyContext` state: `{ ticker: string; companyName: string; priceSnapshot: string } | null`
- [ ] `MarketDataGrid` receives `onBuy` prop that sets `buyContext` and opens `BuyPanel`
- [ ] `BuyPanel` is rendered as a modal/overlay when `buyContext` is non-null; receives `ticker`, `companyName`, `priceSnapshot`, `accountId` (from selected account in Zustand slice), and `onClose`
- [ ] `onClose` clears `buyContext`, closing the panel
- [ ] If no active account is selected (selector is empty), the "Buy" context menu item is disabled or absent — consistent with "No accounts available." state from the Account Selector Specification
- [ ] No business logic in the page component — all order logic in `BuyPanel` and `usePlaceOrder`
- [ ] Unit tests: right-click → onBuy → BuyPanel opens with correct props; onClose → panel closes; no active account → Buy option absent/disabled
**Depends on:** COMP-1, COMP-2

---

## Dependency Summary

| Task ID | Title | Depends on |
|---|---|---|
| DB-1 | `Order` JPA entity and enums | none |
| REPO-1 | `OrderRepository` | DB-1 |
| EXCEPTION-1 | Stocktrading exception classes | none |
| EVT-1 | `OrderFilledEvent`, `OrderRejectedEvent` | none |
| SVC-1 | `LedgerApi` interface | none |
| SVC-2 | `LedgerService.recordStockBuy` implementation | SVC-1 |
| SVC-3 | `MarketDataApi` interface | none |
| SVC-4 | `MarketDataService.getCurrentPrice` implementation | SVC-3 |
| SVC-5 | `StockTradingService.placeOrder` | DB-1, REPO-1, EXCEPTION-1, EVT-1, SVC-1, SVC-2, SVC-3, SVC-4 |
| CONTROLLER-1 | `StocktradingApiDelegateImpl` | SVC-5, API-CONTRACT-1 |
| API-CONTRACT-1 | `stocktrading-openapi.yaml` | none |
| CLI-1 | `placeOrder` Axios call | API-CONTRACT-1 |
| STATE-1 | `usePlaceOrder` mutation hook | CLI-1 |
| COMP-1 | Grid row context menu | none |
| COMP-2 | `BuyPanel` component | STATE-1 |
| SCREEN-1 | Wire buy flow into `TradeStockPage` | COMP-1, COMP-2 |
