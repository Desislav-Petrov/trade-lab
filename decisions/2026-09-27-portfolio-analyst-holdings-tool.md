# Decision: Portfolio Analyst Holdings Tool — Grounded Retrieval via the Portfolio `api/` Interface

**Date:** 2026-09-27  
**Status:** accepted

## Context

Issue #196 asks that the Portfolio Analyst sub-agent be given a tool to read a
user's real portfolio data through the Portfolio domain. Until now the Portfolio
Analyst has had **no tools** and could only answer from the language model's
general knowledge and its system prompt.

This directly reverses part of `decisions/2026-09-21-agent-query-streaming-endpoint.md`,
which stated (point 3) that the Portfolio Analyst "is provided with no tools at
this stage" and (point 6) that when tools are added they must use existing
domain `api/` interfaces. The no-tools stance is now superseded; the api/-only
tool-access rule from that decision **still stands** and is honoured here.

The Portfolio domain already exposes retrieval endpoints on its REST contract —
`GET /api/v1/portfolio/holdings` (priced holdings, cash, and insights) and
`GET /api/v1/portfolio/fills` (paginated fill history). The issue requires the
retrieval capability to be re-exposed through a Kotlin `api/` interface backed by
the **same service class** — no logic is duplicated for the agent — and then
registered as a tool on the Portfolio Analyst.

The following points were settled during Phase 1 sparring and are recorded here
before any domain docs or code land.

### 1. Holdings only — fills are not exposed as a tool

The Portfolio Analyst is granted a single **holdings tool**. The fill-history
endpoint (`GET /api/v1/portfolio/fills`, the Advanced Insights data source) is
**not** exposed to the agent at this stage. Grounding answers in current priced
holdings and unrealised P&L is the primary need; trade-fill history can be added
later as a separate tool if required.

### 2. Single account per conversation

An `AgentConversation` already carries exactly one `accountId` — the account
selected in the frontend when the conversation began. The holdings tool operates
over that single `accountId` only. The agent cannot enumerate or reason across a
user's other accounts within a conversation. This consumes the `accountId` that
was previously "carried for future tool use" and not read by any behaviour.

### 3. Ownership and `userId` enforcement preserved

Moving retrieval from a REST endpoint to an `api/` interface must not weaken
security. The tool is invoked with the conversation's `accountId` and the
JWT-resolved `userId`; the agent cannot substitute a different user or account.
The Portfolio service performs the same ownership check it performs for the REST
endpoint (the account's `userId` must match the caller's `userId`).

### 4. Errors are surfaced to the user

When the tool call fails — account not owned, account not found, or price/balance
data unavailable — the Portfolio Analyst surfaces the error to the user rather
than silently falling back to general-knowledge answers. This keeps grounded
answers honest: the user is told the portfolio could not be retrieved instead of
receiving a confident but ungrounded reply.

## Decision

1. Expose Portfolio **holdings** retrieval through a Kotlin interface in
   `portfolio.api`. The implementation reuses the existing Portfolio service that
   backs `GET /api/v1/portfolio/holdings` — **no duplicated logic**.
2. Register that `api/` interface as a **single tool** on the Portfolio Analyst
   sub-agent. Fills/trade history are **not** exposed.
3. The tool is scoped to the conversation's single `accountId`. Cross-account
   reasoning within a conversation is out of scope.
4. The tool is invoked with the conversation's `accountId` and the JWT-resolved
   `userId`. Ownership is enforced exactly as for the REST endpoint.
5. On tool failure (not owned / not found / data unavailable) the Portfolio
   Analyst **surfaces the error to the user**; it does not fall back to an
   ungrounded answer.

## Consequences

- The Portfolio domain adds a retrieval interface in `portfolio.api` (holdings)
  implemented by its existing service. No new business logic is written; the
  interface is a published contract over the existing read path.
- The `agent` domain registers the Portfolio `api/` interface as an ADK tool on
  the Portfolio Analyst. Per the standing api/-only rule, the agent domain must
  not introduce direct repository or database access to support the tool.
- The following docs are updated in the same change and reference this decision:
  - `domain/flows/agent-assistant-chat.md` — the "no tools" statements are
    replaced; a new **Flow E — Portfolio Analyst Holdings Tool Call** is added,
    with tool-failure error cases.
  - `domain/usecases/chat-with-ai-assistant.md` — Happy Path shows the grounded
    tool call; Failure Scenarios add the tool-failure case; Out of Scope now
    excludes fills/trade history and cross-account reasoning rather than all
    data-grounded analysis.
  - `domain/model/agent-conversation.md` — `accountId` is now consumed by the
    holdings tool to scope retrieval.
- The no-tools stance of `decisions/2026-09-21-agent-query-streaming-endpoint.md`
  (points 3 and 6, no-tools portion) is **superseded**. Its api/-only tool-access
  rule and JWT-resolved `userId` rule remain in force.
- No change to `standards/architecture.md` is required: `agent` (horizontal) may
  depend on `portfolio` (horizontal) via its `api/` interface, and `portfolio`
  already includes an `api/` sub-package in the standard structure.
- Fill-history and cross-account tooling remain available as future work, each to
  be added with its own decision entry.
