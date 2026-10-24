# Decision: AI Assistant can execute trades via a Trade Executor sub-agent

**Date:** 2026-10-24  
**Status:** accepted

## Context

GitHub issue #224 asks for a sub-agent that can place buy and sell orders on the
user's behalf from the AI Assistant popup. A user may request several orders in a
single message; they should execute one at a time and the user should receive a
full per-order report, including backend-surfaced errors such as insufficient
funds.

This reverses two previously documented positions:

- `domain/usecases/chat-with-ai-assistant.md` listed "Actions that mutate state
  (placing trades, changing settings) initiated from the assistant" as **out of
  scope**.
- `domain/flows/agent-assistant-chat.md` described a read-only agent hierarchy
  whose only sub-agent (Portfolio Analyst) reads holdings and never mutates
  state.

Order placement today is exposed only over REST (`POST /api/v1/stock-orders`)
with a browser-generated `Idempotency-Key`. The agent has no button to click and
no browser client to generate that key.

## Decision

1. Add a second sub-agent, the **Trade Executor**, under the existing Main Agent.
   It is reachable through the same assistant popup and the same
   `POST /api/v1/agent/query` endpoint — no new agent endpoint is introduced.
2. The Trade Executor supports **`MARKET` orders only**. The issue's "order type"
   maps to the existing `Order.side` (`BUY` / `SELL`); no new order types are
   added.
3. The agent **proposes** the parsed batch and executes only after a **single
   chat confirmation** covering the whole batch. Confirmation is expressed as a
   normal chat reply.
4. On confirmation, orders execute **one at a time**. A rejection or error on one
   order does **not** stop the remaining orders; each outcome is reported
   individually. There is no cross-order transaction.
5. Execution goes through a **new Stock Trading `api/` interface** that reuses the
   exact order service layer behind `POST /api/v1/stock-orders`. Validation, fund
   and holding checks, ledger writes, and `Order` persistence are identical to the
   manual buy/sell flows — no logic is duplicated for the agent.
6. The backend generates **one `idempotencyKey` per proposed order** at proposal
   time and reuses it on execution, so a resend, retry, or dropped stream cannot
   double-place an order.
7. The proposed-but-unconfirmed batch is held as agent session state, modelled as
   the new `TradeProposal` concept (`domain/model/trade-proposal.md`).

## Consequences

- `domain/usecases/chat-with-ai-assistant.md` no longer excludes trade placement
  wholesale; that use case now covers only the read-only Portfolio Analyst
  experience, and trade execution is a separate journey
  (`domain/usecases/execute-trades-via-assistant.md`).
- `domain/flows/agent-assistant-chat.md` now documents a two-sub-agent hierarchy
  (Portfolio Analyst + Trade Executor) and the delegation rule routes trade
  requests to the Trade Executor.
- The Stock Trading domain gains an `api/` interface for order placement, used
  cross-domain by the `agent` domain. The REST controller and the `api/`
  interface share one service. This applies the existing cross-domain `api/`
  pattern in `standards/architecture.md`; no architecture standard changes.
- `Order.idempotencyKey` is no longer always client-generated: agent-initiated
  orders carry a backend-generated key. `domain/model/order.md` is updated to
  reflect this.
- The assistant is no longer strictly read-only. Any future statement of an
  "assistant never mutates state" invariant is out of date and must be read as
  "the Portfolio Analyst never mutates state."
