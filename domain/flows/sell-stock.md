# Sell Stock

## Overview

Allows an authenticated user to sell shares from an existing portfolio holding directly from the Portfolio page (`/portfolio`). The user right-clicks a stock row in the holdings table, reviews the indicative price (fetched from the backend at panel-open time), enters a quantity, and confirms. The backend reads the current cached price at execution time, checks the account holds sufficient shares, and — only if the holding check passes — writes two ledger entries (`DEBIT/STOCK_SELL` + `CREDIT/CASH`) atomically before marking the order `FILLED`. If the holding check fails the order is marked `REJECTED` and no ledger entries are written. The order is settled synchronously within the same request.

Cash rows in the portfolio table do not expose a sell option. Only stock rows trigger the sell panel.

---

## Flow A — Open Sell Panel

The user initiates a sale from the portfolio holdings table.

### Actors

- **Authenticated User**: A logged-in user viewing the holdings table on `/portfolio`.
- **Guest Browser**: The React frontend rendering the sell panel.
- **System**: The Stock Trading backend providing the indicative price.

### Preconditions

- The user has an active session (is logged in).
- The holdings table is populated with at least one stock row (quantity > 0).
- The user has an active account selected in the account selector.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Right-click a stock row | Right-clicks any stock holding row (non-cash) in the holdings table. |
| 2 | Guest Browser | Show context menu | Displays a context menu with a single option: "Sell". Cash rows do not trigger this menu. |
| 3 | Authenticated User | Click "Sell" | Selects the "Sell" option from the context menu. |
| 4 | Guest Browser | Fetch indicative price | Calls `GET /api/v1/stock-orders/indicative-price?ticker={ticker}` to retrieve the current `indicativePrice` from the backend. Shows a loading state while the fetch is in progress. |
| 5 | Guest Browser | Open sell panel | Opens the sell panel (modal or side panel). Captures the returned `indicativePrice` as `priceSnapshot`. Displays: ticker, company name, order type selector (pre-set to `MARKET`, read-only), quantity input, maximum sellable quantity (`maxQuantity` = current holding quantity from the portfolio row), and a real-time estimated proceeds display. |
| 6 | Guest Browser | Generate idempotency key | Generates a new UUID (`idempotencyKey`) client-side. This key is held in local state for the duration of this sell panel session. |

### Postconditions

- The sell panel is open showing the `priceSnapshot` and `maxQuantity` for the selected ticker.
- A fresh `idempotencyKey` UUID is held in component state.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Indicative price fetch fails | `GET /api/v1/stock-orders/indicative-price` returns non-2xx | Panel shows error: "Could not load price. Please try again." Panel does not open. |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |

---

## Flow B — Enter and Validate Quantity

The user enters a share quantity and reviews the real-time estimated proceeds.

### Actors

- **Authenticated User**: A logged-in user interacting with the sell panel.
- **Guest Browser**: The React frontend validating input and updating the proceeds display.

### Preconditions

- The sell panel is open (Flow A has completed).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Enter quantity | Types a share quantity into the quantity input. May be a positive decimal (e.g. `50.5`) or a positive whole number (e.g. `50`). |
| 2 | Guest Browser | Validate input client-side | Enforces: quantity is a positive number greater than zero and less than or equal to `maxQuantity`. Displays an inline field error if either condition is violated. Disables the Confirm button until the value is valid. |
| 3 | Guest Browser | Update proceeds display | Calculates and displays the estimated proceeds as `quantity × priceSnapshot` in real time as the user types. Labelled "Estimated proceeds" to make clear this is indicative. |

### Postconditions

- A valid quantity is entered (0 < quantity ≤ maxQuantity).
- The estimated proceeds display reflects the current input.
- The Confirm button is enabled.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Quantity is zero or negative | `quantity` ≤ 0 | Inline field error: "Quantity must be greater than zero." Confirm button disabled. |
| Quantity exceeds holding | `quantity` > `maxQuantity` | Inline field error: "Quantity cannot exceed your holding of {maxQuantity} shares." Confirm button disabled. |
| Quantity is not a number | Non-numeric input | Inline field error: "Please enter a valid number." Confirm button disabled. |

---

## Flow C — Confirm Order

The user submits the sell order. The backend validates, fills or rejects, and returns the result. Ledger entries are written **only** after the holding check passes — a rejected order never touches the ledger.

### Actors

- **Authenticated User**: A logged-in user confirming the sale.
- **Guest Browser**: The React frontend submitting the order and displaying the result.
- **System**: The Stock Trading backend processing the order.

### Preconditions

- The sell panel is open with a valid quantity entered (Flow B has completed).
- A funded, active account is selected in the account selector.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click "Confirm" (green tick) | Submits the order. |
| 2 | Guest Browser | Show loading state | Disables both Confirm and Decline buttons. Shows a loading indicator. |
| 3 | Guest Browser | Submit order | Calls `POST /api/v1/stock-orders` with body `{ accountId, ticker, quantity, side: "SELL", orderType: "MARKET", priceSnapshot }` and header `Idempotency-Key: {idempotencyKey}`. |
| 4 | System | Validate request | Checks: `quantity` > 0, `orderType` is `MARKET`, `side` is `SELL`, `ticker` exists in supported config, `accountId` resolves to an account belonging to the authenticated user with `status: active`. Returns the appropriate error code immediately if any check fails (HTTP 400 / 403 / 404). No order record is created on validation failure. |
| 5 | System | Check idempotency key | Checks whether `idempotencyKey` already exists. If it does, returns HTTP 409 immediately. No order record is created. |
| 6 | System | Persist order as PENDING | Creates the `Order` record with `status: PENDING` and `side: SELL` and saves it. |
| 7 | System | Read execution price | Reads the current `currentPrice` from the `MarketDataSnapshot` cache for the requested `ticker` via the Market Data `api/` interface. Sets this as `executionPrice`. |
| 8 | System | Check holding | Reads the account's current share position for `ticker` via the Portfolio `api/` interface. **If `position.quantity` < `quantity`:** transitions order to `REJECTED`, sets `rejectionReason: "Quantity exceeds holding"`, saves. **No ledger entries are written.** Emits `OrderRejectedEvent`. Returns HTTP 200 with `status: REJECTED`. |
| 9 | System | Write ledger entries | Reached **only** when the holding check passes (step 8 passed). Calls `LedgerApi.recordTransaction(...)` twice within the same `@Transactional` scope: (1) `DEBIT / STOCK_SELL`: `amount = quantity` (share quantity), `currency` = account base currency, `ticker = ticker`, `description = "Sell {ticker} x{quantity}"`; (2) `CREDIT / CASH`: `amount = quantity × executionPrice`, `currency` = account base currency, `ticker = null`, `description = "Sell {ticker} x{quantity}"`. `Account.balance` is credited by `quantity × executionPrice`. |
| 10 | System | Transition order to FILLED | Updates `Order.status` to `FILLED`, sets `executionPrice`, saves. Emits `OrderFilledEvent` with `side: SELL`. |
| 11 | System | Return HTTP 200 | Response body: `{ orderId, status: "FILLED", ticker, quantity, side: "SELL", executionPrice, totalProceeds, accountId, createdAt }`. `totalProceeds = quantity × executionPrice`. |
| 12 | Guest Browser | Show fill confirmation | Replaces the sell panel content with a confirmation state: ticker, quantity, execution price per share, and total proceeds (`quantity × executionPrice`). Displays a green tick icon and message "Order filled". |
| 13 | Guest Browser | Refresh portfolio holdings | Invalidates the TanStack Query cache for `GET /api/v1/portfolio/holdings?accountId={accountId}` so the updated holdings are re-fetched on next render. |
| 14 | Guest Browser | Refresh account balance | Invalidates the TanStack Query cache for `GET /api/v1/accounts?userId={userId}&status=active` so the updated cash balance is re-fetched on next render. |

### Postconditions

- An `Order` record exists with `status: FILLED` and `side: SELL`.
- Two `LedgerEntry` rows exist: `DEBIT / STOCK_SELL` and `CREDIT / CASH`.
- `Account.balance` has increased by `quantity × executionPrice`.
- `OrderFilledEvent` (side: SELL) has been emitted.
- The Portfolio domain consumes the event and decrements `Position.quantity` by `quantity`. If `quantity` reaches zero, the position row is retained with `quantity = 0` and will no longer appear in the holdings table.
- The sell panel shows the fill confirmation with `executionPrice`-based proceeds.
- The holdings table and account selector will reflect updated values on next fetch.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Quantity exceeds holding | `position.quantity` < `quantity` at execution time | System returns HTTP 200 with `status: REJECTED`, `rejectionReason: "Quantity exceeds holding"`. Panel shows rejection message. Order is persisted as `REJECTED`. **No ledger entries written.** |
| Ticker not in supported list | `ticker` not in config | System returns HTTP 400. Panel shows generic error message. No order created. |
| Account not found | `accountId` does not resolve | System returns HTTP 404. Panel shows generic error message. No order created. |
| Account not owned by user | Resolved account's `userId` ≠ session user | System returns HTTP 403. Panel shows generic error message. No order created. |
| Account not active | Account `status` is `suspended` or `closed` | System returns HTTP 403. Panel shows generic error message. No order created. |
| Duplicate idempotency key | `Idempotency-Key` already exists in the database | System returns HTTP 409. Panel shows generic error message. A new `idempotencyKey` is generated client-side; user may retry. |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |
| Server error | Any unhandled backend failure | System returns HTTP 500. Panel shows generic error message. |

---

## Flow D — Decline Order

The user cancels the sell panel without submitting.

### Actors

- **Authenticated User**: A logged-in user choosing not to proceed.
- **Guest Browser**: The React frontend closing the panel.

### Preconditions

- The sell panel is open (Flow A has completed).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click "Decline" (red cross) | Clicks the decline button at any point while the panel is open. |
| 2 | Guest Browser | Close sell panel | Dismisses the panel without making any API call. No order is created. All panel state (quantity, idempotency key, priceSnapshot) is discarded. |

### Postconditions

- No order has been created.
- No ledger entries have been written.
- The holdings table and account selector are unchanged.

---

## Events Emitted

- **OrderFilledEvent**: Emitted at Flow C step 10. Payload: `orderId`, `accountId`, `userId`, `ticker`, `quantity`, `side: SELL`, `executionPrice`, `timestamp`.
- **OrderRejectedEvent**: Emitted at Flow C step 8 (rejection path). Payload: `orderId`, `accountId`, `userId`, `ticker`, `quantity`, `side: SELL`, `rejectionReason`, `timestamp`.

---

## Domain Models Involved

- **Order**: Created at Flow C step 6 with `side: SELL`; updated at steps 8 and 10. All fields written.
- **LedgerEntry**: Two rows written at Flow C step 9 via `LedgerApi.recordTransaction` — **only on the FILLED path**. `DEBIT / STOCK_SELL` and `CREDIT / CASH`.
- **Account**: Validated at Flow C step 4 for ownership. `balance` updated only when order is FILLED (credited).
- **Position**: Read at Flow C step 8 via Portfolio `api/` interface to check holding quantity. Updated by the Portfolio domain after consuming `OrderFilledEvent`.
- **MarketDataSnapshot**: Read at Flow C step 7 via Market Data `api/` interface to obtain `executionPrice`.
- **Session**: Read throughout to resolve `userId` and the selected `accountId` from the `portfolio` Zustand slice.
