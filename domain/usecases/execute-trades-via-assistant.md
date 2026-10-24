# Use Case: Execute Trades via the AI Assistant

## Goal
An authenticated user asks the AI Assistant to place one or more buy/sell stock orders, reviews a single proposed batch, confirms it, and receives a full per-order report of what was executed.

## Actor
Authenticated User — a logged-in user interacting with the Trade Lab AI Assistant popup. The agent acts on their behalf; all orders target the single account carried on the conversation.

## Screen
- **Widget/Popup:** The existing "Trade Lab AI Assistant" popup, docked platform-wide (see `domain/usecases/chat-with-ai-assistant`). No new screen is introduced — trade execution happens inside the same chat.
- **Entry point:** The user types a trade request in the popup.

## Trigger
The user sends a message asking to buy and/or sell stock (e.g. "buy 10 AAPL and sell 5 TSLA").

## Domain Models
- `domain/model/trade-proposal`
- `domain/model/order`
- `domain/model/account`
- `domain/model/ledger-entry`
- `domain/model/agent-conversation`
- `domain/model/session`

## Flows
- `domain/flows/agent-assistant-chat` (Flow B — Send a Message and Stream the Reply) — message transport
- `domain/flows/agent-execute-trades` (Flow A — Parse Request and Propose a Batch)
- `domain/flows/agent-execute-trades` (Flow B — Confirm and Execute the Batch Sequentially)
- `domain/flows/agent-execute-trades` (Flow C — Report Batch Outcomes)
- `domain/flows/agent-execute-trades` (Flow D — Trade Tool Call)
- `domain/flows/agent-execute-trades` (Flow E — Decline or Cancel a Proposal)

## Happy Path
1. In the assistant popup, the user asks to buy and/or sell one or more stocks in a single message.
2. The Main Agent delegates to the Trade Executor, which parses the request into a batch of `MARKET` buy/sell orders, generates one idempotency key per order, and proposes the batch back for confirmation.
3. The user replies "yes" to confirm the whole batch.
4. The Trade Executor executes the orders one at a time via its trade tool, backed by the Stock Trading `api/` interface that reuses the same service as `POST /api/v1/stock-orders`.
5. Each order fills or is rejected; a rejection or error on one order does not stop the rest.
6. The agent streams a full per-order report (filled with price and total, or rejected with reason) plus a summary like "2 of 3 orders filled".
7. The frontend refreshes affected balance and holdings views so they reflect the fills.

## Failure Scenarios
| Scenario | Outcome |
|---|---|
| Request not parseable / ambiguous | No proposal; the agent asks the user to restate or clarify the trade(s). |
| Invalid quantity (zero or negative) | The offending line is not proposed; the agent asks for a valid quantity. |
| User declines the proposal | Proposal is declined; no orders are placed. |
| Ambiguous confirmation reply | The agent asks the user to confirm or cancel; nothing is placed yet. |
| Insufficient funds on a buy | That order is rejected with "Insufficient funds"; remaining orders still execute. |
| Quantity exceeds holding on a sell | That order is rejected with "Quantity exceeds holding"; remaining orders still execute. |
| Duplicate execution (resend/retry) | The reused idempotency key prevents a second placement; the recorded outcome is reported. |
| Ticker not supported / account not owned or inactive | That order is reported as failed; remaining orders continue. |
| Unauthenticated request (HTTP 401) | Frontend redirects to `/login`. |

## Out of Scope
- Non-`MARKET` order types (e.g. LIMIT) — only `MARKET` buy/sell orders are supported.
- Cross-account batches — all orders target the single account carried on the conversation.
- All-or-nothing / atomic batches — there is no cross-order transaction; orders settle independently.
- A dedicated confirm/decline UI control — confirmation is a normal chat reply.
- Portfolio analysis and holdings questions — handled by the read-only Portfolio Analyst (`domain/usecases/chat-with-ai-assistant`).
- Durable history of proposals — `TradeProposal` is transient agent session state; only the resulting `Order` records are durable.
- Scheduled, recurring, or conditional orders, and portfolio rebalancing — out of scope for this iteration.
