# TradeProposal

## Overview

Represents a batch of stock orders that the Trade Executor sub-agent has parsed
from a user's request and proposed back to the user, awaiting a single
confirmation before any order is placed. It exists so that the user's "yes" is
applied to exactly the orders that were shown, and so that the idempotency keys
used at execution are fixed at proposal time — preventing double-placement on a
resend or a dropped stream.

A `TradeProposal` is held as agent session state within the `agent` domain, keyed
by the conversation's `conversationId` (the same ADK session that carries the
conversation). It is transient: created when the Trade Executor proposes a batch,
consumed when the user confirms or declines, and superseded if the user proposes
a different batch. It is never exposed to other domains. See
`domain/flows/agent-execute-trades.md` and
`decisions/2026-10-24-agent-trade-execution.md`.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| conversationId | uuid | yes | The conversation this proposal belongs to; acts as the session key. Resolved from the chat turn. |
| userId | uuid | yes | The authenticated user the orders will be placed for. Resolved server-side from the JWT — never from the request body. |
| accountId | uuid | yes | The single account carried on the conversation. Every order in the batch targets this account. |
| orders | list | yes | Ordered list of proposed orders. Each item carries `ticker`, `side` (`BUY` / `SELL`), `quantity`, a pre-generated `idempotencyKey`, and — once executed — its outcome. Execution preserves this order. |
| status | enum | yes | `AWAITING_CONFIRMATION` \| `CONFIRMED` \| `DECLINED` \| `SUPERSEDED`. Set to `AWAITING_CONFIRMATION` on creation. |
| createdAt | datetime | yes | Timestamp the proposal was created. |
| updatedAt | datetime | yes | Timestamp of the last status change. |

## Behaviors

- **Propose**: The Trade Executor parses the user's request into one or more
  `MARKET` buy/sell orders, generates one `idempotencyKey` per order, builds the
  proposal with `status: AWAITING_CONFIRMATION`, and presents it for confirmation.
- **Confirm**: On an affirmative chat reply, the proposal transitions to
  `CONFIRMED` and its orders are executed one at a time via the Trade Executor's
  trade tool, each using its pre-generated `idempotencyKey`.
- **Decline**: On a negative reply or an explicit cancel, the proposal transitions
  to `DECLINED` and no orders are placed.
- **Supersede**: If the user proposes a new batch while one is still awaiting
  confirmation, the earlier proposal transitions to `SUPERSEDED` and only the new
  proposal can be confirmed.

## Events

_No domain events are emitted. `TradeProposal` is session state internal to the
`agent` domain. The orders it produces emit `OrderFilledEvent` /
`OrderRejectedEvent` from the Stock Trading domain when executed, as usual._

## Relationships

- **Order** (`reference`, one per list item): Each confirmed line is placed as
  exactly one Stock Trading `Order`, carrying the line's pre-generated
  `idempotencyKey`. The `agent` domain holds only the proposed line and its
  outcome — never the `Order` entity. See `domain/model/order.md`.
- **AgentConversation** (`reference`): A proposal belongs to one conversation via
  `conversationId` and targets that conversation's `accountId`. See
  `domain/model/agent-conversation.md`.
- **Account** (`reference`): All orders in the batch execute against the single
  `accountId` carried on the conversation. Reference by ID only.

## Business Rules

- A proposal is created only for an authenticated user whose request carries a
  valid JWT; `userId` is always resolved from the JWT, never from the body.
- Every order in the batch uses `orderType: MARKET` and a `side` of `BUY` or
  `SELL`. No other order types are supported.
- Each order line is assigned its `idempotencyKey` at proposal time; the same key
  is reused on execution so a resend or retry cannot place the order twice.
- No order is placed until the proposal is `CONFIRMED`. A `DECLINED` or
  `SUPERSEDED` proposal never places orders.
- At most one proposal per conversation is `AWAITING_CONFIRMATION` at a time; a
  new proposal supersedes any earlier pending one.
- All orders in a batch target the single `accountId` carried on the
  conversation. Cross-account batches are not supported.
- The proposal is transient agent session state; it is not a durable record and
  is not exposed to other domains.
