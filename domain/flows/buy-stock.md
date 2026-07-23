# Buy Stock

## Overview

Allows an authenticated user to purchase shares in a stock at market price from the Stock Trading page. The user right-clicks a ticker row in the market data grid, reviews the indicative price, enters a quantity, and confirms. The backend reads the current cached price at execution time, checks the selected account has sufficient funds, and — only if funds are sufficient — writes two ledger entries (cash debit + stock credit) atomically before marking the order `FILLED`. If funds are insufficient the order is marked `REJECTED` and no ledger entries are written. The order is settled synchronously within the same request.

---

## Flow A — Open Buy Panel

The user initiates a purchase from the market data grid.

### Actors

- **Authenticated User**: A logged-in user viewing the market data grid on `/trade`.
- **Guest Browser**: The React frontend rendering the buy panel.

### Preconditions

- The user has an active session (is logged in).
- The market data grid is populated with at least one ticker row.
- The user has at least one active account selected in the account selector.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Right-click a ticker row | Right-clicks any row in the market data grid. |
| 2 | Guest Browser | Show context menu | Displays a context menu with a single option: "Buy". |
| 3 | Authenticated User | Click "Buy" | Selects the "Buy" option from the context menu. |
| 4 | Guest Browser | Open buy panel | Opens the buy panel (modal or side panel). Captures the `currentPrice` from the grid row at the moment of right-click as `priceSnapshot`. Displays: ticker, company name, order type selector (pre-set to `MARKET`, read-only), quantity input, and a real-time cost display. |
| 5 | Guest Browser | Generate idempotency key | Generates a new UUID (`idempotencyKey`) client-side. This key is held in local state for the duration of this buy panel session. |

### Postconditions

- The buy panel is open showing the pre-captured `priceSnapshot` for the selected ticker.
- A fresh `idempotencyKey` UUID is held in component state.

---

## Flow B — Enter and Validate Quantity

The user enters a share quantity and reviews the real-time cost estimate.

### Actors

- **Authenticated User**: A logged-in user interacting with the buy panel.
- **Guest Browser**: The React frontend validating input and updating the cost display.

### Preconditions

- The buy panel is open (Flow A has completed).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Enter quantity | Types a share quantity into the quantity input. May be a positive decimal (e.g. `0.75`) or a positive whole number (e.g. `2`). |
| 2 | Guest Browser | Validate input client-side | Enforces: quantity is a positive number greater than zero. Displays an inline field error if violated. Disables the Confirm button until the value is valid. |
| 3 | Guest Browser | Update cost display | Calculates and displays the estimated cost as `quantity × priceSnapshot` in real time as the user types. Labelled "Estimated cost" to make clear this is indicative. |

### Postconditions

- A valid quantity is entered.
- The estimated cost display reflects the current input.
- The Confirm button is enabled.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|--------|
| Quantity is zero or negative | `quantity` ≤ 0 | Inline field error: "Quantity must be greater than zero." Confirm button disabled. |
| Quantity is not a number | Non-numeric input | Inline field error: "Please enter a valid number." Confirm button disabled. |

---

## Flow C — Confirm Order

The user submits the buy order. The backend validates, fills or rejects, and returns the result. Ledger entries are written **only** after the fund check passes — a rejected order never touches the ledger.

### Actors

- **Authenticated User**: A logged-in user confirming the purchase.
- **Guest Browser**: The React frontend submitting the order and displaying the result.
- **System**: The Stock Trading backend processing the order.

### Preconditions

- The buy panel is open with a valid quantity entered (Flow B has completed).
- A funded, active account is selected in the account selector.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click "Confirm" (green tick) | Submits the order. |
| 2 | Guest Browser | Show loading state | Disables both Confirm and Decline buttons. Shows a loading indicator. |
| 3 | Guest Browser | Submit order | Calls `POST /api/v1/stock-orders` with body `{ accountId, ticker, quantity, orderType: "MARKET", priceSnapshot }` and header `Idempotency-Key: {idempotencyKey}`. |
| 4 | System | Validate request | Checks: `quantity` > 0, `orderType` is `MARKET`, `ticker` exists in supported config, `accountId` resolves to an account belonging to the authenticated user with `status: active`. Returns the appropriate error code immediately if any check fails (HTTP 400 / 403 / 404). No order record is created on validation failure. |
| 5 | System | Check idempotency key | Checks whether `idempotencyKey` already exists. If it does, returns HTTP 409 immediately. No order record is created. |
| 6 | System | Persist order as PENDING | Creates the `Order` record with `status: PENDING` and saves it. |
| 7 | System | Read execution price | Reads the current `currentPrice` from the `MarketDataSnapshot` cache for the requested `ticker` via the Market Data `api/` interface. Sets this as `executionPrice`. |
| 8 | System | Check funds | Calculates required cash = `quantity × executionPrice`. Reads the account's current `balance`. **If `balance` < required cash:** transitions order to `REJECTED`, sets `rejectionReason: "Insufficient funds"`, saves. **No ledger entries are written.** Emits `OrderRejectedEvent`. Returns HTTP 200 with `status: REJECTED`. |
| 9 | System | Write ledger entries | Reached **only** when funds are sufficient (step 8 passed). Calls `LedgerApi.recordTransaction(...)` twice within the same `@Transactional` scope: (1) `DEBIT / CASH`: `amount = quantity × executionPrice`, `currency` = account base currency, `ticker = null`, `description = "Buy {ticker} x{quantity}"`; (2) `CREDIT / STOCK_BUY`: `amount = quantity` (share quantity), `currency` = account base currency, `ticker = ticker`, `description = "Buy {ticker} x{quantity}"`. `Account.balance` is debited by `quantity × executionPrice`. |
| 10 | System | Transition order to FILLED | Updates `Order.status` to `FILLED`, sets `executionPrice`, saves. Emits `OrderFilledEvent`. |
| 11 | System | Return HTTP 200 | Response body: `{ orderId, status: "FILLED", ticker, quantity, executionPrice, totalCost, accountId, createdAt }`. `totalCost = quantity × executionPrice`. |
| 12 | Guest Browser | Show fill confirmation | Replaces the buy panel content with a confirmation state: ticker, quantity, execution price per share, and total cost (`quantity × executionPrice`). Displays a green tick icon and message "Order filled". |
| 13 | Guest Browser | Refresh account balance | Invalidates the TanStack Query cache for `GET /api/v1/accounts?userId={userId}&status=active` so the updated balance is re-fetched on next render. |

### Postconditions

- An `Order` record exists with `status: FILLED`.
- Two `LedgerEntry` rows exist: `DEBIT / CASH` and `CREDIT / STOCK_BUY`.
- `Account.balance` has decreased by `quantity × executionPrice`.
- `OrderFilledEvent` has been emitted.
- The buy panel shows the fill confirmation with `executionPrice`-based cost.
- The account selector will reflect the updated balance on next fetch.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|--------|
| Insufficient funds | `balance` < `quantity × executionPrice` | System returns HTTP 200 with `status: REJECTED`, `rejectionReason: "Insufficient funds"`. Panel shows rejection message. Order is persisted as `REJECTED`. **No ledger entries written. Ledger balance unchanged.** |
| Ticker not in supported list | `ticker` not in config | System returns HTTP 400. Panel shows generic error message. No order created. |
| Account not found | `accountId` does not resolve | System returns HTTP 404. Panel shows generic error message. No order created. |
| Account not owned by user | Resolved account's `userId` ≠ session user | System returns HTTP 403. Panel shows generic error message. No order created. |
| Account not active | Account `status` is `suspended` or `closed` | System returns HTTP 403. Panel shows generic error message. No order created. |
| Duplicate idempotency key | `Idempotency-Key` already exists in the database | System returns HTTP 409. Panel shows generic error message. No order created. A new `idempotencyKey` is generated client-side; user may retry. |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |
| Server error | Any unhandled backend failure | System returns HTTP 500. Panel shows generic error message. |

---

## Flow D — Decline Order

The user cancels the buy panel without submitting.

### Actors

- **Authenticated User**: A logged-in user choosing not to proceed.
- **Guest Browser**: The React frontend closing the panel.

### Preconditions

- The buy panel is open (Flow A has completed).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click "Decline" (red cross) | Clicks the decline button at any point while the panel is open. |
| 2 | Guest Browser | Close buy panel | Dismisses the panel without making any API call. No order is created. All panel state (quantity, idempotency key, priceSnapshot) is discarded. |

### Postconditions

- No order has been created.
- No ledger entries have been written.
- The market data grid and account selector are unchanged.

---

## Events Emitted

- **OrderFilledEvent**: Emitted at Flow C step 10. Payload: `orderId`, `accountId`, `userId`, `ticker`, `quantity`, `executionPrice`, `timestamp`.
- **OrderRejectedEvent**: Emitted at Flow C step 8 (rejection path). Payload: `orderId`, `accountId`, `userId`, `ticker`, `quantity`, `rejectionReason`, `timestamp`.

---

## Domain Models Involved

- **Order**: Created at Flow C step 6; updated at steps 8 and 10. All fields written.
- **LedgerEntry**: Two rows written at Flow C step 9 via `LedgerApi.recordTransaction` — **only on the FILLED path**. `DEBIT / CASH` and `CREDIT / STOCK_BUY`.
- **Account**: Read at Flow C step 8 for balance check (via `LedgerApi` or account lookup). `balance` updated only when order is FILLED.
- **MarketDataSnapshot**: Read at Flow C step 7 via Market Data `api/` interface to obtain `executionPrice`.
- **Session**: Read throughout to resolve `userId` and the selected `accountId` from the `stocktrading` Zustand slice.
