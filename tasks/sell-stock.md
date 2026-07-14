# Tasks: Sell Stock

**Use case:** `domain/usecases/view-portfolio.md` (sell-stock journey)  
**Flows:** `domain/flows/sell-stock` (A–D), `domain/flows/view-portfolio` (E), `domain/flows/aggregate-stock-position` (Flow B — now active for SELL)  
**Models:** `Order` (side field), `LedgerEntry` (DEBIT/STOCK_SELL + CREDIT/CASH), `Position` (UpdatePositionOnSell), `MarketDataSnapshot` (indicative price), `Account` (balance credited)  
**Events:** `OrderFilledEvent(side=SELL)`, `OrderRejectedEvent(side=SELL)`

---

## DB Layer

---

### DB-1 — Add `side` enum and field to the `Order` entity

**Layer:** Database  
**Domain:** stocktrading  
**Use case:** sell-stock  
**Implements:** `sell-stock` Flow C step 6 — Order record created with `side: SELL`; `buy-stock` Flow C step 6 — existing orders carry `side: BUY`  
**Inputs:**
- `domain/model/order.md` — `side: BUY | SELL` (enum, required, immutable)

**Outputs:**
- `OrderSide` enum class in `org.dpp.tradelab.stocktrading.model`: values `BUY`, `SELL`
- `Order` entity updated: `side: OrderSide` field annotated `@Enumerated(EnumType.STRING)`, `@Column(nullable = false, updatable = false)`

**Acceptance criteria:**
- [ ] `OrderSide` enum exists in `stocktrading.model` with values `BUY` and `SELL`
- [ ] `Order` entity has a `side: OrderSide` field annotated `@Column(nullable = false, updatable = false)` and `@Enumerated(EnumType.STRING)`
- [ ] All existing `Order` construction sites compile — `side` must be supplied at construction
- [ ] Unit test: `Order` entity can be constructed with `side = BUY` and `side = SELL`

**Depends on:** none

---

## EVT Layer — Stock Trading

---

### EVT-1 — Add `side` and `idempotencyKey` to `OrderFilledEvent` and `OrderRejectedEvent`

**Layer:** Event  
**Domain:** stocktrading  
**Use case:** sell-stock  
**Implements:** `sell-stock` Flow C step 10 — `OrderFilledEvent` emitted with `side: SELL`; `aggregate-stock-position` Flow A preconditions — event carries `idempotencyKey` and `side`  
**Inputs:**
- `OrderFilledEvent` data class (existing) in `stocktrading.messaging`
- `OrderRejectedEvent` data class (existing) in `stocktrading.messaging`
- `domain/model/order.md` Events section: both payloads must include `side`; `OrderFilledEvent` must also include `idempotencyKey`

**Outputs:**
- `OrderFilledEvent` updated: adds `side: OrderSide` and `idempotencyKey: UUID` fields
- `OrderRejectedEvent` updated: adds `side: OrderSide` field
- All existing publishers of these events updated to supply the new fields

**Acceptance criteria:**
- [ ] `OrderFilledEvent` has `side: OrderSide` and `idempotencyKey: UUID` fields
- [ ] `OrderRejectedEvent` has `side: OrderSide` field
- [ ] All existing callers that publish these events supply the new fields (BUY path: `side = OrderSide.BUY`)
- [ ] Unit test: event data classes carry the correct field values when constructed

**Depends on:** DB-1

---

## REPO Layer — Portfolio

---

### REPO-1 — Add `findByAccountIdAndTicker` to `PositionRepository`

**Layer:** Repository  
**Domain:** portfolio  
**Use case:** sell-stock  
**Implements:** `sell-stock` Flow C step 8 — holding check requires reading current position quantity for a given account + ticker  
**Inputs:**
- `Position` entity (existing)
- Query: find single `Position` by `accountId: UUID` and `ticker: String`

**Outputs:**
- `PositionRepository`: new method `findByAccountIdAndTicker(accountId: UUID, ticker: String): Optional<Position>`

**Acceptance criteria:**
- [ ] Method `findByAccountIdAndTicker` exists on `PositionRepository`
- [ ] Returns `Optional.empty()` when no matching row exists
- [ ] Repository test: persisting a `Position` and querying by `accountId` + `ticker` returns the correct row
- [ ] Repository test: querying for a non-existent combination returns `Optional.empty()`

**Depends on:** none

---

## SVC Layer — Portfolio: expose PortfolioApi for holding check

---

### SVC-1 — Add `getPositionQuantity` to `PortfolioApi` interface and implement it

**Layer:** Service  
**Domain:** portfolio  
**Use case:** sell-stock  
**Implements:** `sell-stock` Flow C step 8 — Stock Trading reads current share holding via Portfolio `api/` interface  
**Inputs:**
- `accountId: UUID`, `ticker: String`
- `PositionRepository.findByAccountIdAndTicker` (REPO-1)

**Outputs:**
- `PortfolioApi` interface in `portfolio.api`: new method `getPositionQuantity(accountId: UUID, ticker: String): BigDecimal`
- Portfolio service implementation: returns `position.quantity` if a row exists; returns `BigDecimal.ZERO` if no position row exists

**Acceptance criteria:**
- [ ] `PortfolioApi` interface in `portfolio.api` declares `getPositionQuantity(accountId: UUID, ticker: String): BigDecimal`
- [ ] Portfolio service implements the method and is annotated `@Transactional(readOnly = true)`
- [ ] Returns `BigDecimal.ZERO` when no `Position` row exists for the combination
- [ ] Returns the correct `quantity` when a `Position` row exists
- [ ] Unit tests: happy path (position exists), zero path (no position row)

**Depends on:** REPO-1

---

## EXCEPTION Layer — Stock Trading

---

### EXCEPTION-1 — Add `InsufficientHoldingException`

**Layer:** Exception  
**Domain:** stocktrading  
**Use case:** sell-stock  
**Implements:** `sell-stock` Flow C step 8 error case — "Quantity exceeds holding"  
**Inputs:**
- Domain rule: sell `quantity` > current position quantity → rejection reason `"Quantity exceeds holding"`

**Outputs:**
- `InsufficientHoldingException` class in `stocktrading.exception`; constructor accepts `ticker: String`, `requested: BigDecimal`, `available: BigDecimal`
- Note: this exception is caught internally by the service to transition the order to `REJECTED` — it does **not** propagate to the HTTP layer; the controller always returns HTTP 200 for FILLED and REJECTED outcomes

**Acceptance criteria:**
- [ ] `InsufficientHoldingException` exists in `stocktrading.exception` with `ticker`, `requested`, and `available` fields
- [ ] Unit test: exception carries the correct field values when constructed

**Depends on:** none

---

## SVC Layer — Stock Trading: indicative price

---

### SVC-2 — Add `getIndicativePrice` to `StockTradingService`

**Layer:** Service  
**Domain:** stocktrading  
**Use case:** sell-stock  
**Implements:** `sell-stock` Flow A step 4 — backend returns current price for a ticker when the sell panel opens  
**Inputs:**
- `ticker: String`
- `MarketDataApi.getCurrentPrice(ticker): BigDecimal` (existing cross-domain interface)

**Outputs:**
- `StockTradingService`: new `getIndicativePrice(ticker: String): BigDecimal` method annotated `@Transactional(readOnly = true)`
- Validates that `ticker` exists in the supported tickers config; throws typed exception if not
- Returns the `BigDecimal` `currentPrice` from `MarketDataApi`

**Acceptance criteria:**
- [ ] Method is `@Transactional(readOnly = true)`
- [ ] Throws `UnsupportedTickerException` (or equivalent) when `ticker` is not in the supported list
- [ ] Returns the `BigDecimal` price from `MarketDataApi` for a valid ticker
- [ ] Unit tests: happy path, unsupported ticker throws exception

**Depends on:** none

---

## SVC Layer — Stock Trading: sell order processing

---

### SVC-3 — Add sell order processing to `StockTradingService`

**Layer:** Service  
**Domain:** stocktrading  
**Use case:** sell-stock  
**Implements:** `sell-stock` Flow C steps 4–10 — validate request, check idempotency, persist PENDING order, read execution price, check holding, write ledger entries or reject, transition to FILLED/REJECTED, emit events  
**Inputs:**
- `accountId: UUID`, `userId: UUID`, `ticker: String`, `quantity: BigDecimal`, `side: OrderSide = SELL`, `orderType: OrderType = MARKET`, `priceSnapshot: BigDecimal`, `idempotencyKey: UUID`
- `PortfolioApi.getPositionQuantity(accountId, ticker)` (SVC-1)
- `MarketDataApi.getCurrentPrice(ticker)` (existing)
- `LedgerApi.recordTransaction(...)` × 2 (existing)
- `OrderRepository.findByIdempotencyKey(...)`, `OrderRepository.save(...)` (existing)
- Account ownership + status validation (existing pattern from buy flow)

**Outputs:**
- `StockTradingService`: `placeOrder(...)` method extended (or a new `placeSellOrder(...)` method) annotated `@Transactional`, branching on `side`
- **FILLED path:** `Order` saved with `status: FILLED`, `executionPrice` set; two ledger entries written — `DEBIT/STOCK_SELL` (`amount = quantity`, `ticker = ticker`, `description = "Sell {ticker} x{quantity}"`) and `CREDIT/CASH` (`amount = quantity × executionPrice`, `ticker = null`, `description = "Sell {ticker} x{quantity}"`); `Account.balance` credited; `OrderFilledEvent(side=SELL, idempotencyKey=...)` published
- **REJECTED path (quantity exceeds holding):** `Order` saved with `status: REJECTED`, `rejectionReason: "Quantity exceeds holding"`; `OrderRejectedEvent(side=SELL)` published; no ledger entries written
- Returns: `orderId`, `status`, `ticker`, `quantity`, `side`, `executionPrice` or `rejectionReason`, `totalProceeds` (`quantity × executionPrice`), `accountId`, `createdAt`

**Acceptance criteria:**
- [ ] Method is `@Transactional`
- [ ] Validates: `quantity > 0`, `orderType == MARKET`, `ticker` in supported list, account exists, account owned by authenticated user, account `status == active` — throws typed exception on each; no `Order` record created on validation failure
- [ ] Throws `DuplicateIdempotencyKeyException` (HTTP 409) if `idempotencyKey` already exists in the database
- [ ] Creates `Order` with `status: PENDING`, `side: SELL` before price and holding checks
- [ ] Reads `executionPrice` from `MarketDataApi`
- [ ] Calls `PortfolioApi.getPositionQuantity(accountId, ticker)`; if `positionQuantity < quantity` → catches `InsufficientHoldingException` internally, transitions order to `REJECTED`, emits `OrderRejectedEvent(side=SELL)`, returns without writing ledger entries
- [ ] On FILLED: calls `LedgerApi.recordTransaction` twice — `DEBIT/STOCK_SELL` then `CREDIT/CASH` — within the same `@Transactional` scope
- [ ] On FILLED: transitions `Order` to `FILLED`, sets `executionPrice`, emits `OrderFilledEvent(side=SELL, idempotencyKey=...)`
- [ ] `totalProceeds = quantity × executionPrice` is returned in the service result
- [ ] Unit tests: FILLED happy path, REJECTED (quantity exceeds holding), each validation error, duplicate idempotency key

**Depends on:** DB-1, EVT-1, SVC-1, EXCEPTION-1

---

## SVC Layer — Portfolio: UpdatePositionOnSell

---

### SVC-4 — Implement `UpdatePositionOnSell` in `PortfolioPositionService`

**Layer:** Service  
**Domain:** portfolio  
**Use case:** sell-stock  
**Implements:** `aggregate-stock-position` Flow B — consume `OrderFilledEvent(side=SELL)`, decrement position, retain row at zero  
**Inputs:**
- `OrderFilledEvent` with `side: SELL`, `quantity`, `executionPrice`, `idempotencyKey`, `accountId`, `userId`, `ticker`
- `PositionRepository.findByAccountIdAndTicker` (REPO-1)
- Existing idempotency check in `handleOrderFilled`

**Outputs:**
- `PortfolioPositionService.handleOrderFilled(event)` updated to branch on `event.side`:
  - `BUY` path: existing behaviour unchanged
  - `SELL` path:
    - Decrements `quantity` by `event.quantity`
    - Decrements `totalCost` proportionally: `totalCost = totalCost × (remainingQuantity / previousQuantity)`
    - Recalculates `avgPrice = totalCost / quantity`; sets `avgPrice = null` when `quantity == 0`
    - Updates `minPrice = min(minPrice, event.executionPrice)`; updates `maxPrice = max(maxPrice, event.executionPrice)`
    - If `quantity` reaches zero: retains row with `quantity = 0`, `totalCost = 0` for audit
    - Sets `lastUpdated = event.timestamp`
    - Idempotency key recorded in the same transaction

**Acceptance criteria:**
- [ ] BUY path behaviour is unchanged
- [ ] SELL path: `quantity` decremented by `event.quantity`
- [ ] SELL path: `totalCost` decremented proportionally
- [ ] SELL path: `avgPrice` recalculated; is `null` when `quantity == 0`
- [ ] SELL path: `minPrice`/`maxPrice` updated if `event.executionPrice` is outside current range
- [ ] SELL path: position row retained with `quantity = 0`, `totalCost = 0` on full sell-out
- [ ] Idempotency: duplicate event with same `idempotencyKey` is discarded silently; no position update
- [ ] Unit tests: partial sell (quantity reduced), full sell-out (quantity reaches zero), duplicate event discarded, BUY path unaffected

**Depends on:** EVT-1, REPO-1

---

## CONTROLLER Layer — Stock Trading

---

### CONTROLLER-1 — Update `StocktradingApiDelegateImpl` for sell orders and indicative price

**Layer:** Controller  
**Domain:** stocktrading  
**Use case:** sell-stock  
**Implements:** `sell-stock` Flow C step 3 — `POST /api/v1/stock-orders` with `side: SELL`; `sell-stock` Flow A step 4 — `GET /api/v1/stock-orders/indicative-price?ticker={ticker}`  
**Inputs:**
- Generated `PlaceOrderRequest` (updated by API-CONTRACT-1 to include `side`)
- Generated `IndicativePriceResponse` (new, from API-CONTRACT-1)
- `StockTradingService.placeOrder(...)` (SVC-3)
- `StockTradingService.getIndicativePrice(ticker)` (SVC-2)

**Outputs:**
- `StocktradingApiDelegateImpl.placeOrder(...)`: passes `side` from `PlaceOrderRequest` to service; returns HTTP 200 for both FILLED and REJECTED (existing contract preserved)
- `StocktradingApiDelegateImpl.getIndicativePrice(ticker)`: new method returning `ResponseEntity<IndicativePriceResponse>` (HTTP 200); returns HTTP 404 for unsupported ticker

**Acceptance criteria:**
- [ ] `placeOrder` passes `side` from `PlaceOrderRequest` to `StockTradingService`
- [ ] `placeOrder` returns HTTP 200 for both FILLED and REJECTED responses
- [ ] `getIndicativePrice` calls `StockTradingService.getIndicativePrice(ticker)` and returns `{ ticker, indicativePrice }` with HTTP 200
- [ ] `getIndicativePrice` returns HTTP 404 when ticker is unsupported
- [ ] `@SpringBootTest` + MockMvc tests: sell order FILLED, sell order REJECTED, indicative price happy path, indicative price unsupported ticker

**Depends on:** SVC-2, SVC-3, EXCEPTION-1

---

## API-CONTRACT Layer

---

### API-CONTRACT-1 — Update `stocktrading-openapi.yaml` for sell orders and indicative price

**Layer:** OpenAPI Contract  
**Domain:** stocktrading  
**Use case:** sell-stock  
**Implements:** `sell-stock` Flow A step 4 — `GET /api/v1/stock-orders/indicative-price`; `sell-stock` Flow C step 3 — `POST /api/v1/stock-orders` extended with `side`  
**Inputs:**
- Existing `services/contract/stocktrading-openapi.yaml`
- New path: `GET /stock-orders/indicative-price?ticker={ticker}` → `IndicativePriceResponse { ticker: string, indicativePrice: number }`
- Updated `PlaceOrderRequest`: add required field `side: enum [BUY, SELL]`
- Updated `PlaceOrderResponse`: add `side: enum [BUY, SELL]` field; add `totalProceeds` (nullable, number) alongside existing `totalCost` (nullable, number)

**Outputs:**
- `services/contract/stocktrading-openapi.yaml` updated with:
  - `GET /stock-orders/indicative-price` path with `ticker` as required query parameter; responses: HTTP 200, 400, 401, 404, 500
  - `IndicativePriceResponse` schema: `ticker: string`, `indicativePrice: number`
  - `PlaceOrderRequest.side` required field: `enum: [BUY, SELL]`
  - `PlaceOrderResponse.side` field: `enum: [BUY, SELL]`
  - `PlaceOrderResponse.totalProceeds` nullable number field

**Acceptance criteria:**
- [ ] `GET /stock-orders/indicative-price` is defined with `ticker` as a required query parameter
- [ ] `IndicativePriceResponse` schema has `ticker: string` and `indicativePrice: number`
- [ ] `PlaceOrderRequest` has `side` as a required field with `enum: [BUY, SELL]`
- [ ] `PlaceOrderResponse` includes `side`, `totalCost` (nullable), and `totalProceeds` (nullable)
- [ ] All existing response codes for `POST /stock-orders` are preserved unchanged
- [ ] `GET /stock-orders/indicative-price` documents HTTP 200, 400, 401, 404, 500 responses
- [ ] YAML is valid OpenAPI 3.0.3

**Depends on:** none

---

## CLI Layer — Frontend

---

### CLI-1 — Add `fetchIndicativePrice` and update `placeOrder` in stocktrading API client

**Layer:** API Client  
**Domain:** stocktrading  
**Use case:** sell-stock  
**Implements:** `sell-stock` Flow A step 4 — `GET /api/v1/stock-orders/indicative-price`; `sell-stock` Flow C step 3 — `POST /api/v1/stock-orders` with `side: SELL`  
**Inputs:**
- `services/contract/stocktrading-openapi.yaml` (API-CONTRACT-1)
- Shared Axios instance from `shared/api/`

**Outputs:**
- `src/domains/stocktrading/api/stockTradingApi.ts` updated:
  - `fetchIndicativePrice(ticker: string): Promise<IndicativePriceResponse>` — calls `GET /api/v1/stock-orders/indicative-price?ticker={ticker}`
  - `placeOrder(request: PlaceOrderRequest): Promise<PlaceOrderResponse>` — request type updated to include `side: 'BUY' | 'SELL'`
- `src/domains/stocktrading/types/index.ts` updated:
  - `PlaceOrderRequest` adds `side: 'BUY' | 'SELL'`
  - `PlaceOrderResponse` adds `side: 'BUY' | 'SELL'` and `totalProceeds: number | null`
  - New `IndicativePriceResponse` type: `{ ticker: string; indicativePrice: number }`

**Acceptance criteria:**
- [ ] `fetchIndicativePrice` calls `GET /api/v1/stock-orders/indicative-price?ticker={ticker}` via the shared Axios instance
- [ ] `placeOrder` request type includes `side: 'BUY' | 'SELL'`
- [ ] `IndicativePriceResponse` type has `ticker: string` and `indicativePrice: number`
- [ ] `PlaceOrderResponse` type has `side`, `totalProceeds: number | null` fields
- [ ] No `any` types used
- [ ] Tests: `fetchIndicativePrice` mocked happy path and error; `placeOrder` with `side: 'SELL'` mocked

**Depends on:** API-CONTRACT-1

---

## STATE Layer — Frontend

---

### STATE-1 — Add `useSellPanel` hook to stocktrading domain

**Layer:** State  
**Domain:** stocktrading  
**Use case:** sell-stock  
**Implements:** `sell-stock` Flow A (panel open/close state, idempotency key, indicative price fetch), Flow B (quantity validation), Flow C (submit sell order, cache invalidation), Flow D (decline/close)  
**Inputs:**
- `fetchIndicativePrice` from CLI-1
- `placeOrder` from CLI-1
- TanStack Query cache keys for `GET /api/v1/portfolio/holdings` and `GET /api/v1/accounts`
- `accountId` from the `portfolio` Zustand slice (selected account)

**Outputs:**
- `src/domains/stocktrading/hooks/useSellPanel.ts` (new):
  - State: `isOpen`, `ticker`, `maxQuantity`, `priceSnapshot`, `idempotencyKey`, `quantity`, `validationError`, `isFetchingPrice`, `priceError`, `isSubmitting`, `submitError`, `result` (FILLED or REJECTED outcome)
  - `openSellPanel(ticker, maxQuantity)`: fetches indicative price → on success sets `priceSnapshot`, generates fresh UUID `idempotencyKey`, sets `isOpen = true`; on failure sets `priceError`, does not open panel
  - `closeSellPanel()`: resets all state
  - `setQuantity(value)`: updates `quantity`, runs client-side validation:
    - Non-numeric → `"Please enter a valid number."`
    - ≤ 0 → `"Quantity must be greater than zero."`
    - > `maxQuantity` → `"Quantity cannot exceed your holding of {maxQuantity} shares."`
    - Valid → clears `validationError`
  - `confirmSell()`: calls `placeOrder` mutation with `side: 'SELL'`, `Idempotency-Key` header, and current panel state; on FILLED invalidates `portfolio/holdings` and `accounts` query caches; on HTTP 409 generates a new `idempotencyKey`

**Acceptance criteria:**
- [ ] `openSellPanel` fetches indicative price; on success sets `priceSnapshot`, generates UUID `idempotencyKey`, sets `isOpen = true`
- [ ] `openSellPanel` sets `priceError` and keeps `isOpen = false` if price fetch fails (`sell-stock` Flow A error case)
- [ ] `setQuantity` sets correct `validationError` for non-numeric, ≤ 0, and > `maxQuantity` inputs
- [ ] `setQuantity` clears `validationError` for a valid input
- [ ] `confirmSell` submits `POST /api/v1/stock-orders` with `side: 'SELL'` and `Idempotency-Key` header
- [ ] On FILLED: `portfolio/holdings` and `accounts` query caches are invalidated
- [ ] On HTTP 409: a new UUID `idempotencyKey` is generated; `submitError` is set
- [ ] `closeSellPanel` resets all state fields to their initial values
- [ ] Hook tests: each state transition, each validation case, cache invalidation on fill, new key generated on 409

**Depends on:** CLI-1

---

## COMP Layer — Frontend

---

### COMP-1 — Build `SellPanel` component

**Layer:** Component  
**Domain:** stocktrading  
**Use case:** sell-stock  
**Implements:** `sell-stock` Flows A–D UI — panel display, quantity input, estimated proceeds, confirm/decline buttons, loading and result states  
**Inputs:**
- `useSellPanel` hook from STATE-1
- `SellPanelProps`: `ticker: string`, `companyName: string`, `maxQuantity: number`

**Outputs:**
- `src/domains/stocktrading/components/SellPanel.tsx`:
  - Displays: ticker, company name, order type (`MARKET`, read-only), quantity input, max quantity hint, estimated proceeds (`quantity × priceSnapshot`, labelled "Estimated proceeds", updated in real time)
  - Confirm button (green tick): disabled when `validationError !== null` or `isSubmitting === true`; calls `confirmSell()`
  - Decline button (red cross): enabled at all times while panel is open (except during `isSubmitting`); calls `closeSellPanel()`
  - Loading state while `isFetchingPrice` or `isSubmitting`
  - On FILLED: confirmation view — ticker, quantity, execution price per share, total proceeds (`quantity × executionPrice`), green tick icon, message "Order filled"
  - On REJECTED: message "Order rejected: {rejectionReason}"
  - On `priceError`: message "Could not load price. Please try again." (panel does not open — handled upstream)
  - On `submitError` (non-409, non-REJECTED): generic error message

**Acceptance criteria:**
- [ ] Quantity input shows `validationError` as an inline field error when set
- [ ] Confirm button is disabled when `validationError` is set or `isSubmitting` is true
- [ ] Decline button calls `closeSellPanel()` at any panel stage
- [ ] Estimated proceeds updates in real time as quantity changes
- [ ] Fill confirmation displays `executionPrice` and `totalProceeds`
- [ ] Rejection view displays `rejectionReason`
- [ ] Component tests: renders form, shows inline validation error, shows loading state, shows fill confirmation, shows rejection, decline closes panel

**Depends on:** STATE-1

---

### COMP-2 — Add right-click "Sell" context menu to portfolio holdings table stock rows

**Layer:** Component  
**Domain:** portfolio  
**Use case:** sell-stock  
**Implements:** `view-portfolio` Flow E steps 1–3 — right-click stock row → context menu with "Sell" option  
**Inputs:**
- `StockHolding` row data: `ticker: string`, `quantity: number` (as `maxQuantity`)
- `onSell(ticker: string, maxQuantity: number)` callback prop

**Outputs:**
- Portfolio holdings table row component (existing) updated:
  - Right-clicking a stock row opens a context menu with a single option: "Sell"
  - Clicking "Sell" calls `onSell(ticker, quantity)`
  - Cash row does not show a context menu on right-click
  - Context menu dismissed on click-outside or Escape key

**Acceptance criteria:**
- [ ] Right-clicking a stock row displays a context menu with "Sell"
- [ ] Clicking "Sell" invokes `onSell(ticker, quantity)` with correct values from the row
- [ ] Right-clicking the cash row does not display any context menu
- [ ] Context menu is dismissed on Escape or click-outside
- [ ] Component tests: right-click stock row shows menu; clicking Sell fires callback with correct args; cash row shows no menu; Escape dismisses menu

**Depends on:** none

---

## SCREEN Layer — Frontend

---

### SCREEN-1 — Wire `SellPanel` into `PortfolioPage`

**Layer:** Screen  
**Domain:** portfolio  
**Use case:** sell-stock  
**Implements:** `view-portfolio` Flow E step 4; `sell-stock` Flows A–D end-to-end — right-click on portfolio row through to fill confirmation and table refresh  
**Inputs:**
- `HoldingsTableRow` with `onSell` prop (COMP-2)
- `SellPanel` component (COMP-1)
- `useSellPanel` hook (STATE-1): `openSellPanel`, `isOpen`, `ticker`, `maxQuantity`
- Holdings data from existing `usePortfolioHoldings` hook — `companyName` looked up from `StockHolding` by `ticker`

**Outputs:**
- `PortfolioPage` updated:
  - Consumes `useSellPanel`
  - `onSell(ticker, maxQuantity)` handler calls `openSellPanel(ticker, maxQuantity)`
  - Renders `<SellPanel>` conditionally when `isOpen === true`, passing `ticker`, `companyName`, `maxQuantity`
  - After fill confirmation the holdings table re-renders automatically via TanStack Query cache invalidation (no explicit re-fetch call needed in the page)

**Acceptance criteria:**
- [ ] Right-clicking a stock row and selecting "Sell" opens `SellPanel` for that ticker
- [ ] `SellPanel` receives the correct `ticker`, `companyName` (from holdings), and `maxQuantity`
- [ ] Clicking Decline (or after fill) closes `SellPanel`
- [ ] After a successful fill the holdings table refreshes without a manual page reload
- [ ] Cash row does not trigger the sell panel
- [ ] Screen tests: right-click → panel opens with correct props; fill → table refreshes; decline → panel closes; cash row → no panel

**Depends on:** COMP-1, COMP-2, STATE-1

---

## Dependency Summary

| Task ID | Title | Depends on |
|---------|-------|------------|
| DB-1 | Add `side` enum and field to `Order` entity | none |
| EVT-1 | Add `side` + `idempotencyKey` to `OrderFilledEvent` / `OrderRejectedEvent` | DB-1 |
| REPO-1 | Add `findByAccountIdAndTicker` to `PositionRepository` | none |
| SVC-1 | Add `getPositionQuantity` to `PortfolioApi` + implement | REPO-1 |
| EXCEPTION-1 | Add `InsufficientHoldingException` | none |
| SVC-2 | `getIndicativePrice` in `StockTradingService` | none |
| SVC-3 | Sell order processing in `StockTradingService` | DB-1, EVT-1, SVC-1, EXCEPTION-1 |
| SVC-4 | `UpdatePositionOnSell` in `PortfolioPositionService` | EVT-1, REPO-1 |
| CONTROLLER-1 | Update `StocktradingApiDelegateImpl` for sell + indicative price | SVC-2, SVC-3, EXCEPTION-1 |
| API-CONTRACT-1 | Update `stocktrading-openapi.yaml` | none |
| CLI-1 | `fetchIndicativePrice` + updated `placeOrder` in stocktrading API client | API-CONTRACT-1 |
| STATE-1 | `useSellPanel` hook | CLI-1 |
| COMP-1 | `SellPanel` component | STATE-1 |
| COMP-2 | Right-click "Sell" context menu on portfolio row | none |
| SCREEN-1 | Wire `SellPanel` into `PortfolioPage` | COMP-1, COMP-2, STATE-1 |
