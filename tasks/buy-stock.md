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

An authenticated user on `/trade` right-clicks a ticker row in the market data grid and selects "Buy". A buy panel opens, capturing the current grid price as an indicative `priceSnapshot`. The user enters a quantity (fractional or whole) and confirms. The frontend submits `POST /api/v1/stock-orders` with an `Idempotency-Key` header. The backend validates the request, reads the live execution price from the `MarketDataSnapshot` cache (which may differ from `priceSnapshot`), and checks the selected account has sufficient funds. If funds are sufficient, two `LedgerEntry` rows (cash DEBIT + stock CREDIT) are written atomically and the order is marked `FILLED`. If funds are insufficient, the order is marked `REJECTED` and the ledger is left untouched. All within one `@Transactional` scope. The frontend shows the fill confirmation with `executionPrice`-based total cost and invalidates the account balance cache.

**Models involved:** `Order` (new, stocktrading), `LedgerEntry` (ledger), `Account` (ledger), `MarketDataSnapshot` (marketdata), `Session` (frontend only).
**Events emitted:** `OrderFilledEvent`, `OrderRejectedEvent` (both from `stocktrading.messaging`).
**Cross-domain calls:** Stock Trading → Ledger via `LedgerApi` (sync); Stock Trading → Market Data via `MarketDataApi` (sync).

---

## Ambiguities and Gaps

None — all clarified during intake and PR review:
- `LedgerEntry.amount` for stock entries = share quantity (decision logged).
- `executionPrice` is re-read from the cache at fill time (not client-supplied `priceSnapshot`).
- Post-fill UI shows `executionPrice`-based cost, not `priceSnapshot`-based cost.
- `REJECTED` orders are persisted. Ledger is never touched on a rejection.
- Ledger writes happen **after** the fund check passes and **only** on the FILLED path.
- Duplicate idempotency key → HTTP 409, client handles retry.
- Stock Trading calls Ledger synchronously within the same transaction (decision logged).
- `LedgerApi` exposes a generic `recordTransaction` method — not a buy-specific one.
- Endpoint is `POST /api/v1/stock-orders`.

---

## Layer: DB

### DB-1 — Add `Order` JPA entity and enums to `stocktrading.model`

**Layer:** Database
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/model/order.md` — all properties and business rules; `domain/flows/buy-stock.md` Flow C step 6 (persist PENDING), steps 8 and 10 (update to REJECTED/FILLED)
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
**Implements:** `domain/model/order.md` — idempotency key unique constraint; `domain/flows/buy-stock.md` Flow C step 6 (persist order)
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
**Implements:** `domain/model/order.md` Events; `domain/flows/buy-stock.md` Flow C step 10 (`OrderFilledEvent`), step 8 rejection path (`OrderRejectedEvent`)
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
**Implements:** `decisions/2026-07-08-stock-trading-ledger-sync-api.md`; `domain/flows/buy-stock.md` Flow C step 9 (write ledger entries via generic transaction method)
**Inputs:**
- `domain/model/ledger-entry.md` — entry types, `assetType` enum, business rules
- `standards/architecture.md` — cross-domain `api/` interface pattern
- `standards/backend.md` — no imports from `{domain}.model` across boundaries
**Outputs:**
- `org.dpp.tradelab.ledger.api.LedgerApi` — Kotlin interface with one generic method:
```kotlin
fun recordTransaction(
    accountId: UUID,
    userId: UUID,
    type: String,        // "DEBIT" | "CREDIT"
    assetType: String,   // "CASH" | "STOCK_BUY" | "STOCK_SELL"
    amount: BigDecimal,
    currency: String,
    ticker: String?,
    description: String?
)
```
> Note: `type` and `assetType` are passed as `String` to avoid exposing Ledger domain enums across the boundary. The Ledger implementation validates and maps to its internal `EntryType` / `AssetType` enums.
**Acceptance criteria:**
- [ ] Interface is in `org.dpp.tradelab.ledger.api` — not in `service`, `model`, or any other sub-package
- [ ] Method signature matches the output spec exactly — no Ledger domain model types in the signature
- [ ] Interface has no Spring annotations
- [ ] No JPA entity types appear in the method signature
**Depends on:** none

---

### SVC-2 — Implement `LedgerApi.recordTransaction` in `LedgerService`

**Layer:** Service
**Domain:** ledger
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 9 — write `DEBIT/CASH` entry and `CREDIT/STOCK_BUY` entry; deduct cash from `Account.balance`. Called **only** after the fund check in SVC-5 has passed.
**Inputs:**
- `org.dpp.tradelab.ledger.api.LedgerApi` (from SVC-1)
- `org.dpp.tradelab.ledger.model.LedgerEntry` (existing JPA entity)
- `org.dpp.tradelab.ledger.model.Account` (existing JPA entity)
- `org.dpp.tradelab.ledger.repository.LedgerEntryRepository` (existing)
- `org.dpp.tradelab.ledger.repository.AccountRepository` (existing)
- `domain/model/ledger-entry.md` — business rules for stock entries
- `standards/backend.md` — `@Transactional` on write methods
**Outputs:**
- `LedgerService` (existing class in `ledger.service`) implements `LedgerApi`; `recordTransaction` method added
**Acceptance criteria:**
- [ ] `LedgerService` implements `LedgerApi`
- [ ] Method is `@Transactional`
- [ ] Validates `type` maps to a known `EntryType` enum value; throws `IllegalArgumentException` on unknown value
- [ ] Validates `assetType` maps to a known `AssetType` enum value; throws `IllegalArgumentException` on unknown value
- [ ] Resolves account by `accountId`; throws `AccountNotFoundException` (existing ledger exception) if not found
- [ ] Creates a `LedgerEntry` with all provided fields and saves it
- [ ] For `DEBIT / CASH` entries: deducts `amount` from `account.balance` and saves the account. If the deduction would take balance below zero, throws `IllegalStateException` as a safety net (the fund check in SVC-5 is the primary guard — this is a defensive check only)
- [ ] All arithmetic uses `BigDecimal` — no `Double`
- [ ] Unit tests cover: happy path (entry saved, balance deducted on DEBIT/CASH), account not found, unknown entry type, unknown asset type, balance-below-zero safety net
**Depends on:** SVC-1

---

## Layer: SVC (Market Data — cross-domain api/)

### SVC-3 — Add `MarketDataApi` interface to `marketdata.api`

**Layer:** Service
**Domain:** marketdata
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 7 — read current execution price from `MarketDataSnapshot` cache
**Inputs:**
- `domain/model/market-data-snapshot.md` — `currentPrice` field
- `standards/architecture.md` — cross-domain `api/` interface pattern
**Outputs:**
- `org.dpp.tradelab.marketdata.api.MarketDataApi` — Kotlin interface with one method:
  `fun getCurrentPrice(ticker: String): BigDecimal`
**Acceptance criteria:**
- [ ] Interface is in `org.dpp.tradelab.marketdata.api`
- [ ] Throws `IllegalStateException` if the ticker has no cache entry (missing entry is a programming error — cache is seeded at startup)
- [ ] No Spring annotations on the interface
- [ ] No JPA entity types in the signature
**Depends on:** none

---

### SVC-4 — Implement `MarketDataApi.getCurrentPrice` in `MarketDataService`

**Layer:** Service
**Domain:** marketdata
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 7 — reads `MarketDataSnapshot.currentPrice` from in-memory cache
**Inputs:**
- `org.dpp.tradelab.marketdata.api.MarketDataApi` (from SVC-3)
- Existing service that owns the in-memory snapshot cache
- `domain/model/market-data-snapshot.md`
**Outputs:**
- Existing `MarketDataService` (in `marketdata.service`) implements `MarketDataApi`; `getCurrentPrice` method added
**Acceptance criteria:**
- [ ] `MarketDataService` implements `MarketDataApi`
- [ ] `getCurrentPrice(ticker)` reads from the in-memory cache and returns `currentPrice` as `BigDecimal`
- [ ] Throws `IllegalStateException` with a clear message if ticker has no cache entry
- [ ] Unit test: known ticker → correct price; unknown ticker → `IllegalStateException`
**Depends on:** SVC-3

---

## Layer: SVC (Stock Trading — core order placement)

### SVC-5 — Implement `StockTradingService.placeOrder` in `stocktrading.service`

**Layer:** Service
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C steps 4–11 (full order lifecycle)

**Explicit execution order within `placeOrder` (all within one `@Transactional` scope):**
1. Validate `quantity > 0`, `orderType == MARKET`, ticker exists in supported config, account exists and belongs to `userId`, account is active — throw typed exception immediately on failure; **no Order record created**.
2. Check idempotency key — throw `DuplicateIdempotencyKeyException` if already present; **no Order record created**.
3. Create and save `Order` with `status = PENDING`.
4. Read `executionPrice` from `marketDataApi.getCurrentPrice(ticker)`.
5. **Fund check:** if `account.balance < quantity × executionPrice` → update Order to `REJECTED`, set `rejectionReason`, save, emit `OrderRejectedEvent`, return Order. **`ledgerApi.recordTransaction` is NOT called.**
6. **Ledger writes (only reached when funds are sufficient):** call `ledgerApi.recordTransaction(...)` for the DEBIT/CASH entry, then call `ledgerApi.recordTransaction(...)` for the CREDIT/STOCK_BUY entry.
7. Update Order to `FILLED`, set `executionPrice`, save, emit `OrderFilledEvent`, return Order.

**Inputs:**
- `org.dpp.tradelab.stocktrading.model.Order`, `OrderType`, `OrderStatus` (DB-1)
- `org.dpp.tradelab.stocktrading.repository.OrderRepository` (REPO-1)
- `org.dpp.tradelab.stocktrading.exception.*` (EXCEPTION-1)
- `org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent`, `OrderRejectedEvent` (EVT-1)
- `org.dpp.tradelab.ledger.api.LedgerApi` (SVC-1) — constructor injection, interface only
- `org.dpp.tradelab.marketdata.api.MarketDataApi` (SVC-3) — constructor injection, interface only
- A `MarketDataSupportedTickersApi` interface (one method: `fun isTickerSupported(ticker: String): Boolean`) — if not yet present in `marketdata.api`, create it following the same pattern as SVC-3/SVC-4
- `ApplicationEventPublisher` — constructor injection
- Account balance read — via `LedgerApi` or a dedicated `LedgerAccountApi` interface exposing `getBalance(accountId: UUID): BigDecimal`. If the account ownership/status checks require reading the Account entity, a separate `LedgerAccountApi` interface must be defined in `ledger.api` (one method: `fun getAccount(accountId: UUID, userId: UUID): AccountSummary` where `AccountSummary` is a simple data class in `ledger.api` with fields `id`, `userId`, `currency`, `balance`, `status` — no JPA entity crossing the boundary).

**Outputs:**
- `org.dpp.tradelab.stocktrading.service.StockTradingService` — new `@Service` class

**Acceptance criteria:**
- [ ] `StockTradingService` is `@Service`, constructor injection only
- [ ] `placeOrder` is `@Transactional`
- [ ] Steps 1–7 above execute in the documented order
- [ ] `ledgerApi.recordTransaction` is called exactly **twice** on FILLED path (once DEBIT/CASH, once CREDIT/STOCK_BUY) and **zero times** on REJECTED path
- [ ] All `BigDecimal` arithmetic uses `.multiply()` — no `Double` intermediates
- [ ] No imports from `ledger.model`, `ledger.service`, `ledger.repository`, `marketdata.model`, `marketdata.service` — only `*.api` interfaces
- [ ] Unit tests: FILLED happy path (2 `recordTransaction` calls verified, `OrderFilledEvent` emitted), insufficient funds (0 `recordTransaction` calls, `OrderRejectedEvent` emitted), duplicate idempotency key (409, no Order saved), ticker not found (400), account not found (404), account not active (403), account not owned (403)
**Depends on:** DB-1, REPO-1, EXCEPTION-1, EVT-1, SVC-1, SVC-2, SVC-3, SVC-4

---

## Layer: CONTROLLER

### CONTROLLER-1 — Implement `StocktradingApiDelegateImpl` in `stocktrading.controller`

**Layer:** Controller
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 3 (`POST /api/v1/stock-orders`), steps 11 and 8 (HTTP 200 FILLED/REJECTED responses), all error case HTTP codes
**Inputs:**
- Generated `StocktradingApiDelegate` (from API-CONTRACT-1 after code generation)
- `org.dpp.tradelab.stocktrading.service.StockTradingService` (SVC-5)
- `standards/backend.md` — delegate pattern, `@Service` on impl, generated DTOs only
**Outputs:**
- `org.dpp.tradelab.stocktrading.controller.StocktradingApiDelegateImpl` — `@Service` implementing generated `StocktradingApiDelegate`
**Acceptance criteria:**
- [ ] Class is `@Service`, implements `StocktradingApiDelegate`
- [ ] Reads `Idempotency-Key` header and passes as `UUID` to `StockTradingService.placeOrder`
- [ ] Delegates entirely to `StockTradingService.placeOrder` — no business logic
- [ ] Maps returned `Order` to generated `PlaceOrderResponse` DTO
- [ ] FILLED: HTTP 200, body includes `orderId`, `status = "FILLED"`, `ticker`, `quantity`, `executionPrice`, `totalCost` (`quantity × executionPrice`), `accountId`, `createdAt`
- [ ] REJECTED: HTTP 200, body includes `orderId`, `status = "REJECTED"`, `ticker`, `quantity`, `rejectionReason`, `accountId`, `createdAt`; `executionPrice` and `totalCost` null
- [ ] `MockMvc` tests: FILLED (200), REJECTED (200), 400 invalid quantity, 400 bad ticker, 404 account not found, 403 not owned, 403 not active, 409 duplicate key, 401 unauthenticated
**Depends on:** SVC-5, API-CONTRACT-1

---

## Layer: API-CONTRACT

### API-CONTRACT-1 — Create `services/contract/stocktrading-openapi.yaml`

**Layer:** OpenAPI Contract
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 3 (`POST /api/v1/stock-orders`), step 11 (FILLED response), step 8 rejection path (REJECTED response), all error cases
**Inputs:**
- `domain/flows/buy-stock.md` — request/response fields, HTTP status codes
- `domain/model/order.md` — field types, constraints
- `standards/backend.md` — OpenAPI conventions, `info.title = "Trade Lab API — Stocktrading"`, `delegatePattern = true`
**Paths introduced:**
- `POST /api/v1/stock-orders`
**Outputs:**
- `services/contract/stocktrading-openapi.yaml` — new file

**Contract spec:**

`POST /api/v1/stock-orders`
- Request header: `Idempotency-Key` (string/uuid format, required)
- Request body (`PlaceOrderRequest`):
  - `accountId` (string/uuid, required)
  - `ticker` (string, required, minLength 1, maxLength 10)
  - `quantity` (number, required, exclusiveMinimum 0)
  - `orderType` (string enum `["MARKET"]`, required)
  - `priceSnapshot` (number, required, exclusiveMinimum 0)
- Response `200 OK` (`PlaceOrderResponse`):
  - `orderId` (string/uuid)
  - `status` (string enum `["FILLED", "REJECTED"]`)
  - `ticker` (string)
  - `quantity` (number)
  - `executionPrice` (number, nullable)
  - `totalCost` (number, nullable)
  - `rejectionReason` (string, nullable)
  - `accountId` (string/uuid)
  - `createdAt` (string/date-time)
- Error responses: 400, 401, 403, 404, 409, 500 — all use `ErrorResponse` schema (`status`, `error`, `details`)

**Acceptance criteria:**
- [ ] Valid OpenAPI 3.0.3
- [ ] `info.title` is `"Trade Lab API — Stocktrading"`
- [ ] Path is `POST /api/v1/stock-orders`
- [ ] `Idempotency-Key` header declared as required
- [ ] `quantity` and `priceSnapshot` have `exclusiveMinimum: 0`
- [ ] `orderType` restricted to enum `["MARKET"]`
- [ ] All error status codes declared (400, 401, 403, 404, 409, 500)
- [ ] `ErrorResponse` schema defined
- [ ] `./gradlew openApiGenerate` produces `StocktradingApiDelegate` and `StocktradingApiController` without errors
**Depends on:** none

---

## Layer: CLI

### CLI-1 — Add `placeOrder` Axios call to `stocktrading/api/`

**Layer:** API Client
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 3 — `POST /api/v1/stock-orders`
**Inputs:**
- `services/contract/stocktrading-openapi.yaml` (API-CONTRACT-1)
- `standards/frontend.md` — shared Axios instance, typed, `verb + noun` naming
**Outputs:**
- `services/front-end/src/domains/stocktrading/api/ordersApi.ts` — exports:
  - `PlaceOrderRequest` interface
  - `PlaceOrderResponse` interface
  - `ORDERS_QUERY_KEY` constant
  - `placeOrder(idempotencyKey: string, request: PlaceOrderRequest): Promise<PlaceOrderResponse>` — POSTs to `/api/v1/stock-orders`, sets `Idempotency-Key` header
**Acceptance criteria:**
- [ ] Uses shared Axios instance — no new instance
- [ ] `Idempotency-Key` header set on every call
- [ ] All types explicit — no `any`
- [ ] Unit test: verifies POST to `/api/v1/stock-orders` with correct body and header
**Depends on:** API-CONTRACT-1

---

## Layer: STATE

### STATE-1 — Add `usePlaceOrder` mutation hook to `stocktrading/hooks/`

**Layer:** State
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow C step 2 (loading), step 3 (submit), step 12 (show confirmation), step 13 (invalidate account cache)
**Inputs:**
- `placeOrder` from CLI-1
- TanStack Query `useMutation`
- `standards/frontend.md` — TanStack Query owns server state; invalidate on success
**Outputs:**
- `services/front-end/src/domains/stocktrading/hooks/usePlaceOrder.ts` — `usePlaceOrder()` hook; on success invalidates `ACCOUNTS_QUERY_KEY`
**Acceptance criteria:**
- [ ] Uses `useMutation` from TanStack Query
- [ ] On success invalidates `ACCOUNTS_QUERY_KEY`
- [ ] Mutation variables include `idempotencyKey` and `PlaceOrderRequest`
- [ ] No Zustand store access
- [ ] Unit test (`renderHook`): success → ACCOUNTS_QUERY_KEY invalidated; error → error state accessible
**Depends on:** CLI-1

---

## Layer: COMP

### COMP-1 — Add grid row context menu with "Buy" option to `MarketDataGrid`

**Layer:** Component
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow A steps 1–3
**Inputs:**
- Existing `MarketDataGrid` component
- `standards/frontend.md` — functional components, explicit props, no API calls
**Outputs:**
- `MarketDataGrid` extended with `onBuy(ticker: string, companyName: string, priceSnapshot: string)` prop
- Context menu on right-click: single "Buy" option
**Acceptance criteria:**
- [ ] Right-clicking a row shows context menu with exactly one item: "Buy"
- [ ] Clicking "Buy" invokes `onBuy(ticker, companyName, priceSnapshot)` where `priceSnapshot` = `currentPrice` at right-click time
- [ ] Outside click or Escape dismisses without invoking `onBuy`
- [ ] `onBuy` declared in `MarketDataGridProps`
- [ ] No API calls inside component
- [ ] Unit tests: right-click shows menu; "Buy" click invokes `onBuy`; outside click dismisses
**Depends on:** none

---

### COMP-2 — Create `BuyPanel` component in `stocktrading/components/`

**Layer:** Component
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/flows/buy-stock.md` Flow A step 4, Flow B steps 1–3, Flow C steps 1–2 and 12, Flow D steps 1–2
**Inputs:**
- `usePlaceOrder` hook (STATE-1)
- `domain/usecases/trade-stock-page.md` — Buy Panel Specification
- `standards/frontend.md` — functional component, explicit props
**Outputs:**
- `services/front-end/src/domains/stocktrading/components/BuyPanel.tsx`
- `BuyPanelProps`: `{ ticker, companyName, priceSnapshot, accountId, onClose }`
**Acceptance criteria:**
- [ ] On mount generates UUID `idempotencyKey` in `useState`
- [ ] Displays ticker, company name, order type ("MARKET", read-only), quantity input, "Estimated cost" = `quantity × priceSnapshot` in real time
- [ ] Inline errors: "Quantity must be greater than zero." / "Please enter a valid number."; Confirm disabled until valid
- [ ] Confirm = green tick icon; Decline = red cross icon
- [ ] On Confirm: disables both buttons, shows loading; calls `usePlaceOrder` with `{ idempotencyKey, accountId, ticker, quantity, orderType: 'MARKET', priceSnapshot }`
- [ ] On FILLED response: shows fill confirmation with `executionPrice`, `totalCost`, green tick, "Order filled"
- [ ] On REJECTED response (HTTP 200, `status: 'REJECTED'`): shows "Order rejected: {rejectionReason}"
- [ ] On error (non-200): shows generic error; re-enables buttons; generates new `idempotencyKey`
- [ ] On Decline: calls `onClose()` immediately, no API call
- [ ] No direct Axios/query calls — only through `usePlaceOrder`
- [ ] Unit tests: initial render; estimated cost updates; confirm calls mutation; FILLED confirmation; REJECTED message; decline calls `onClose`
**Depends on:** STATE-1

---

## Layer: SCREEN

### SCREEN-1 — Wire `BuyPanel` and context menu into `TradeStockPage`

**Layer:** Screen
**Domain:** stocktrading
**Use case:** buy-stock
**Implements:** `domain/usecases/trade-stock-page.md` happy path steps 10–13; failure: no active accounts → Buy blocked
**Inputs:**
- `BuyPanel` (COMP-2)
- `MarketDataGrid` with `onBuy` prop (COMP-1)
- Existing `TradeStockPage`
- `standards/frontend.md` — pages assemble components; no business logic
**Outputs:**
- `TradeStockPage` updated: `buyContext` state, `onBuy` passed to grid, `BuyPanel` rendered conditionally
**Acceptance criteria:**
- [ ] `buyContext: { ticker, companyName, priceSnapshot } | null` in page state
- [ ] `MarketDataGrid` receives `onBuy` that sets `buyContext`
- [ ] `BuyPanel` rendered as modal/overlay when `buyContext` non-null; receives `ticker`, `companyName`, `priceSnapshot`, `accountId` (from Zustand slice), `onClose`
- [ ] `onClose` clears `buyContext`
- [ ] No active account → Buy context menu item absent/disabled
- [ ] No business logic in page component
- [ ] Unit tests: right-click → onBuy → BuyPanel opens; onClose → closes; no account → Buy absent
**Depends on:** COMP-1, COMP-2

---

## Dependency Summary

| Task ID | Title | Depends on |
|---|---|---|
| DB-1 | `Order` JPA entity and enums | none |
| REPO-1 | `OrderRepository` | DB-1 |
| EXCEPTION-1 | Stocktrading exception classes | none |
| EVT-1 | `OrderFilledEvent`, `OrderRejectedEvent` | none |
| SVC-1 | `LedgerApi` interface (generic `recordTransaction`) | none |
| SVC-2 | `LedgerService.recordTransaction` implementation | SVC-1 |
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
