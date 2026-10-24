# Agent Execute Trades

## Overview

Covers the **Trade Executor** sub-agent: the part of the Trade Lab AI Assistant
that places stock orders on the user's behalf. From the same assistant popup and
the same `POST /api/v1/agent/query` endpoint used for chat, a user can ask the
agent to buy and/or sell one or more stocks in a single message. The Main Agent
delegates such requests to the Trade Executor, which parses them into a batch of
`MARKET` buy/sell orders, **proposes** the batch, and — only after a single chat
confirmation — executes the orders **one at a time**, then reports each outcome.

Execution reuses the exact order logic behind `POST /api/v1/stock-orders` via a
Stock Trading domain `api/` interface, so validation, fund/holding checks, ledger
writes, and `Order` persistence are identical to the manual buy/sell flows
(`domain/flows/buy-stock.md`, `domain/flows/sell-stock.md`). The backend generates
one `idempotencyKey` per proposed order at proposal time and reuses it on
execution, preventing double-placement. Backend-surfaced outcomes (e.g.
insufficient funds, quantity exceeds holding) are reported back per order.

This flow reverses the previously read-only stance of the assistant — see
`decisions/2026-10-24-agent-trade-execution.md`. The proposed-but-unconfirmed
batch is modelled as `TradeProposal` (`domain/model/trade-proposal.md`). The
Trade Executor is the only sub-agent that mutates state; the read-only Portfolio
Analyst and the overall widget behaviour are unchanged — see
`domain/flows/agent-assistant-chat.md`. Message transport, SSE streaming, and the
widget lifecycle are inherited from that flow (Flow A, Flow B); this document
covers only what is specific to executing trades.

---

## Flow A — Parse Request and Propose a Batch

The user asks the agent to buy and/or sell stock. The Main Agent delegates to the
Trade Executor, which parses the request into a batch and proposes it for
confirmation. No order is placed in this flow.

### Actors

- **Authenticated User**: A logged-in user typing a trade request in the popup.
- **Guest Browser**: The React frontend sending the query and rendering the streamed proposal.
- **System (Agent)**: The Main Agent and the Trade Executor sub-agent.

### Preconditions

- The assistant popup is open and the user has an active session (JWT).
- An account is selected in frontend state and carried on the conversation.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Request one or more trades | Sends a message such as "buy 10 AAPL and sell 5 TSLA" via the normal query path (agent-assistant-chat Flow B). |
| 2 | System (Agent) | Delegate to Trade Executor | The Main Agent recognises a trade-execution intent and delegates to the Trade Executor; it never places trades itself. |
| 3 | System (Agent) | Parse into a batch | The Trade Executor parses the message into an ordered list of `MARKET` orders, each with `ticker`, `side` (`BUY` / `SELL`), and `quantity`. |
| 4 | System (Agent) | Generate idempotency keys | Generates one `idempotencyKey` per parsed order and builds a `TradeProposal` (`status: AWAITING_CONFIRMATION`) keyed by `conversationId`, scoped to the conversation's `accountId` and JWT-resolved `userId`. Any earlier pending proposal for the conversation is `SUPERSEDED`. |
| 5 | System (Agent) | Stream the proposal | Streams back a readable summary of the batch (each order's side, quantity, ticker, and the target account) and asks the user to confirm. |
| 6 | Guest Browser | Render the proposal | Renders the proposal in the popup and awaits the user's reply. |

### Postconditions

- A `TradeProposal` exists with `status: AWAITING_CONFIRMATION`.
- No `Order` has been created; no ledger entries have been written.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Nothing parseable | No order can be extracted from the message | No proposal is created; the Trade Executor asks the user to restate the trade(s). |
| Ambiguous order | A ticker, side, or quantity is unclear | No proposal (or a partial proposal excluding the ambiguous line); the Trade Executor asks the user to clarify before proposing. |
| Invalid quantity | A requested quantity is zero or negative | The offending line is not proposed; the Trade Executor explains and asks for a valid quantity. |
| No account on conversation | No account is selected in frontend state | Send is blocked upstream (agent-assistant-chat Flow B); the user is prompted to select an account first. |
| Unauthenticated request | No valid JWT | System returns HTTP 401. Frontend redirects to `/login`. |

---

## Flow B — Confirm and Execute the Batch Sequentially

The user confirms the proposed batch in chat. The Trade Executor executes each
order one at a time and continues through the batch even if an order is rejected
or errors.

### Actors

- **Authenticated User**: A logged-in user confirming the proposal.
- **System (Agent)**: The Trade Executor and its trade tool.
- **System (Stock Trading)**: The Stock Trading backend, reached via its `api/` interface.

### Preconditions

- A `TradeProposal` for the conversation is `AWAITING_CONFIRMATION` (Flow A).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Confirm in chat | Replies affirmatively (e.g. "yes", "go ahead") in the popup. |
| 2 | System (Agent) | Interpret confirmation | The Main Agent delegates to the Trade Executor, which matches the reply to the pending proposal and transitions it to `CONFIRMED`. |
| 3 | System (Agent) | Execute order-by-order | For each order in list order, invokes the trade tool (Flow D) with the line's `ticker`, `side`, `quantity`, and pre-generated `idempotencyKey`, plus the conversation's `accountId` and JWT-resolved `userId`. |
| 4 | System (Agent) | Record each outcome | Captures each order's result — `FILLED` (with `executionPrice` and total), `REJECTED` (with reason), or an error — onto the proposal line. |
| 5 | System (Agent) | Continue past failures | A rejection or error on one order does **not** stop the batch; the Trade Executor proceeds to the next order. |
| 6 | System (Agent) | Report outcomes | After all orders are attempted, streams the full per-order report (Flow C). |

### Postconditions

- Each confirmed order was attempted exactly once (idempotency-key protected).
- Every `FILLED` order has a persisted `Order`, two `LedgerEntry` rows, and an updated `Account.balance`, exactly as in the manual buy/sell flows.
- The `TradeProposal` is `CONFIRMED` with each line's outcome recorded.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| User declines | Reply is negative or a cancel | Proposal transitions to `DECLINED`; no orders placed (Flow E). |
| Ambiguous reply | Reply is neither a clear yes nor no | The Trade Executor asks the user to confirm or cancel; the proposal stays `AWAITING_CONFIRMATION`. |
| No pending proposal | The confirmation arrives with no `AWAITING_CONFIRMATION` proposal | Treated as a new request; the Trade Executor asks what to trade (Flow A). |
| One order rejected | A single order returns `REJECTED` (e.g. insufficient funds) | That order is reported as rejected with its reason; remaining orders still execute. |
| One order errors | The trade tool call errors for one order | That order is reported as failed; remaining orders still execute. |

---

## Flow C — Report Batch Outcomes

After the batch is attempted, the Trade Executor streams a complete per-order
report so the user knows exactly what happened.

### Actors

- **System (Agent)**: The Trade Executor composing the report.
- **Guest Browser**: The React frontend rendering the streamed report.

### Preconditions

- Flow B has attempted every order in the batch.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Agent) | Compose per-order lines | For each order: `FILLED` → side, quantity, ticker, execution price per share, and total cost (buy) or proceeds (sell); `REJECTED` → side, quantity, ticker, and reason (e.g. "Insufficient funds"); error → a safe failure message. |
| 2 | System (Agent) | Add a summary line | Adds an aggregate (e.g. "2 of 3 orders filled"). |
| 3 | System (Agent) | Stream the report | Streams the report back via SSE as the closing turn of the interaction. |
| 4 | Guest Browser | Render and refresh stale views | Renders the report. Because trades were placed outside the trading/portfolio pages, the frontend refreshes affected views (account balances and portfolio holdings) so they reflect the new state. |

> **Open implementation detail (left to decomposition):** how the frontend learns a trade-execution turn occurred and which caches to invalidate (e.g. a structured signal accompanying the reply) is not prescribed here. The behaviour — stale balance/holdings views are refreshed after fills — is fixed; the mechanism is an implementation choice.

### Postconditions

- The user has a complete per-order outcome report for the batch.
- Balance and holdings views reflect any fills.

---

## Flow D — Trade Tool Call (per order)

For each confirmed order, the Trade Executor's trade tool places the order through
the Stock Trading `api/` interface, which reuses the same service as the REST
order endpoint.

### Actors

- **System (Agent)**: The Trade Executor and its registered trade tool.
- **System (Stock Trading)**: The Stock Trading backend, reached via its `api/` interface — the same service behind `POST /api/v1/stock-orders`.

### Preconditions

- The proposal is `CONFIRMED` (Flow B) and this order has not yet been placed.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Agent) | Invoke the tool | Calls the trade tool with `ticker`, `side`, `quantity`, the line's `idempotencyKey`, the conversation's `accountId`, and the JWT-resolved `userId`. The agent cannot substitute a different account or user. |
| 2 | System (Stock Trading) | Place via shared service | The `api/` interface delegates to the same order service as `POST /api/v1/stock-orders`: validates the request, persists the `Order` as `PENDING`, reads `executionPrice` from the Market Data cache, checks funds (BUY) or holding (SELL), writes the two ledger entries on success, transitions to `FILLED` or `REJECTED`, and emits `OrderFilledEvent` / `OrderRejectedEvent`. `priceSnapshot` is taken from the current cached price at placement. |
| 3 | System (Stock Trading) | Return the outcome | Returns a structured result: `orderId`, `status`, and either `executionPrice` + total or `rejectionReason`. |
| 4 | System (Agent) | Record on the proposal line | The Trade Executor records the outcome and moves to the next order. |

### Postconditions

- Exactly one `Order` exists for this line (or the prior one is returned on a duplicate key); its ledger and balance effects match the manual flow when `FILLED`.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Insufficient funds (BUY) | Account balance < cost | `Order` persisted as `REJECTED` with `rejectionReason: "Insufficient funds"`; reported to the user. No ledger entries. |
| Quantity exceeds holding (SELL) | Position < quantity | `Order` persisted as `REJECTED` with `rejectionReason: "Quantity exceeds holding"`; reported to the user. No ledger entries. |
| Duplicate idempotency key | The line was already placed (e.g. retry) | The service returns HTTP 409 / the existing result; the order is not placed twice. Reported from the recorded outcome. |
| Ticker not supported | `ticker` not in config | Validation error; reported as a failed line. Remaining orders continue. |
| Account not owned / not active | Account `userId` ≠ session user, or status not active | The service rejects (403); reported as a failed line. |
| Market/ledger unavailable | Price or ledger write fails | The order errors; reported as a failed line. Remaining orders continue. |

---

## Flow E — Decline or Cancel a Proposal

The user chooses not to proceed with a proposed batch.

### Actors

- **Authenticated User**: A logged-in user declining the proposal.
- **System (Agent)**: The Trade Executor.

### Preconditions

- A `TradeProposal` for the conversation is `AWAITING_CONFIRMATION`.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Decline or cancel | Replies negatively (e.g. "no", "cancel") in the popup. |
| 2 | System (Agent) | Discard the proposal | The Trade Executor transitions the proposal to `DECLINED` and confirms nothing was placed. |

### Postconditions

- The proposal is `DECLINED`; no `Order` was created and no ledger entries were written.

---

## Events Emitted

- **OrderFilledEvent / OrderRejectedEvent**: Emitted by the Stock Trading domain (not the `agent` domain) for each executed order, exactly as in `domain/flows/buy-stock.md` and `domain/flows/sell-stock.md`. The Trade Executor emits no events of its own.

---

## Domain Models Involved

- **TradeProposal**: Created in Flow A; confirmed/declined/superseded across Flows B and E; carries the per-order idempotency keys and outcomes. Transient agent session state. See `domain/model/trade-proposal.md`.
- **Order**: One created per confirmed line via the Stock Trading `api/` interface in Flow D, with a backend-generated `idempotencyKey`. See `domain/model/order.md`.
- **Account**: The single `accountId` carried on the conversation funds every order in the batch; `balance` changes on each fill. Reference by ID only.
- **LedgerEntry**: Two rows written per `FILLED` order via the shared order service, identical to the manual flows.
- **MarketDataSnapshot**: Read at fill time to set `executionPrice` (and `priceSnapshot`) for each order.
- **Session**: `userId` is resolved server-side from the JWT and passed to the trade tool; the frontend `accessToken` is attached via the shared Axios interceptor.
