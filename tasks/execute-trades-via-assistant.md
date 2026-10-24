# Tasks: Execute Trades via the AI Assistant

**Use case:** `domain/usecases/execute-trades-via-assistant.md`
**Flows:** `domain/flows/agent-execute-trades.md` (A–E), transported over `domain/flows/agent-assistant-chat.md` (Flow B)
**Decision:** `decisions/2026-10-24-agent-trade-execution.md`
**Issue:** #224

---

## Context the implementer must know

- The **agent domain is ADK-based**. Sub-agents are `LlmAgent` beans wired in `config/AgentConfiguration.kt`; tools are `@Component` classes in `agent/tool/` exposing a `FunctionTool` (see `agent/tool/PortfolioHoldingsTool.kt` as the reference pattern, including the `ToolScope` that reads `accountId`/`userId` from `ToolContext` and validates against the authenticated user).
- The reusable settlement path is `StockTradingService.placeOrder(idempotencyKey, accountId, userId, ticker, quantity, orderType, priceSnapshot, side): Order`. It validates, persists the order, reads the execution price, checks funds (BUY) / holding (SELL), writes ledger entries, transitions FILLED/REJECTED, and emits `OrderFilledEvent`/`OrderRejectedEvent`. Insufficient funds / quantity-exceeds-holding come back as a **REJECTED `Order`** (not an exception); hard validation failures are **thrown** (`TickerNotFoundException`, `OrderAccountNotFoundException`, `OrderAccountNotOwnedException`, `OrderAccountNotActiveException`, `DuplicateIdempotencyKeyException`).
- The cross-domain pattern is: interface + DTOs in `{domain}.api`, implementation (`*ApiAdapter`) in `{domain}.service` (see `portfolio/api/PortfolioApi.kt` + `portfolio/service/PortfolioApiAdapter.kt`).
- `AgentService.query()` returns a stream that **strips tool/function-response parts** — only natural-language text reaches the client. The SSE writer in `AgentApiDelegateImpl` already supports **named events** (`event: error`). The frontend client (`queryAgent.ts`) currently reads only `data:` lines and ignores the `event:` name.

## Design decisions locked in (Phase 1 spar)

1. Propose the parsed batch, then a **single chat confirmation** for the whole batch.
2. **MARKET only**; "order type" means `side` (`BUY`/`SELL`). No new order types.
3. **Continue on failure**; each order's outcome reported individually. No cross-order transaction.
4. Execution via a **new Stock Trading `api/` interface that reuses `StockTradingService`**.
5. **Backend-generated idempotency key per proposed order**, reused on execution to prevent double-placement.
6. Stale balance/holdings views are refreshed after fills via a **structured `trade-executed` SSE event** (Option B).

## Notes & deviations

- **No stocktrading OpenAPI task:** the agent reaches Stock Trading through a cross-domain **Kotlin `api/` interface**, not HTTP. There is no new REST endpoint on the stocktrading side, so no `stocktrading-openapi.yaml` change.
- **`TradeProposal` is ADK session state**, not a JPA entity (like `ConversationSummary`/`AgentUserProfile`). Therefore there are **no DB or REPO tasks**.
- There **is** an agent `API-CONTRACT` task — for the new `trade-executed` SSE event on the existing `POST /api/v1/agent/query` operation (no new path/operation).
- Buffered (non-SSE) fallback remains text-only; the refresh signal is not delivered on that rare degraded path (documented limitation).

---

## Stock Trading domain — API

### [API-1] — Define `StockTradingApi` cross-domain interface + result types

**Layer:** API (`stocktrading.api`)
**Domain:** stocktrading
**Use case:** execute-trades-via-assistant
**Implements:** agent-execute-trades Flow D steps 1–3; decision #5
**Inputs:** placement parameters — `idempotencyKey: UUID`, `accountId: UUID`, `userId: UUID`, `ticker: String`, `quantity: BigDecimal`, `side` (api-local enum)
**Outputs:**
- `StockTradingApi` interface: `placeMarketOrder(idempotencyKey, accountId, userId, ticker, quantity, side): OrderPlacementResult`
- `OrderPlacementResult` data class: `orderId: UUID`, `status` (`OrderPlacementStatus { FILLED, REJECTED }`), `ticker`, `quantity`, `side`, `executionPrice: BigDecimal?`, `totalAmount: BigDecimal?`, `rejectionReason: String?`
- api-local enums `OrderPlacementSide { BUY, SELL }`, `OrderPlacementStatus { FILLED, REJECTED }`
**Acceptance criteria:**
- [ ] Interface + DTOs live in `stocktrading/api/`; mirror the `ledger.api` / `portfolio.api` style
- [ ] No `stocktrading.model` / `stocktrading.service` types leak across the boundary (no `Order`, `OrderSide`, etc.)
- [ ] `OrderPlacementResult` carries everything the agent needs to report a line: fill price + total on FILLED, `rejectionReason` on REJECTED
- [ ] `executionPrice`/`totalAmount` null on REJECTED; `rejectionReason` null on FILLED
**Depends on:** none

---

## Stock Trading domain — SVC

### [SVC-1] — Implement `StockTradingApiAdapter` reusing `StockTradingService.placeOrder`

**Layer:** SVC (`stocktrading.service`)
**Domain:** stocktrading
**Use case:** execute-trades-via-assistant
**Implements:** agent-execute-trades Flow D step 2; `order.md` "Place via Stock Trading `api/` (agent-initiated)"
**Inputs:** `StockTradingApi.placeMarketOrder(...)` parameters
**Outputs:** `@Service StockTradingApiAdapter : StockTradingApi` that resolves `orderType = MARKET`, resolves `priceSnapshot` from the current cached price (`MarketDataApi.getCurrentPrice(ticker)`), calls `stockTradingService.placeOrder(...)`, and maps the returned `Order` → `OrderPlacementResult` (`totalAmount = quantity × executionPrice` on FILLED)
**Acceptance criteria:**
- [ ] Reuses existing `StockTradingService` — no duplicated validation / fund / holding / ledger / event logic
- [ ] Maps FILLED (with `executionPrice` + `totalAmount`) and REJECTED (with `rejectionReason`) correctly
- [ ] Service exceptions (`TickerNotFoundException`, `OrderAccountNotFoundException`, `OrderAccountNotOwnedException`, `OrderAccountNotActiveException`, `DuplicateIdempotencyKeyException`) propagate unchanged
- [ ] Unit tests (KoTest + mockito-kotlin) mock `StockTradingService` + `MarketDataApi`; cover FILLED mapping, REJECTED mapping, and propagation of each exception
**Depends on:** API-1

---

## Agent domain — SVC

### [SVC-2] — `TradeProposalService` — manage the proposal in ADK session state

**Layer:** SVC (`agent.service`)
**Domain:** agent
**Use case:** execute-trades-via-assistant
**Implements:** `trade-proposal.md`; Flow A step 4; Flow B steps 2 & 6; Flow E step 2; decision #6/#7
**Inputs:** parsed order lines (`ticker`, `side`, `quantity`), conversation/session context (`conversationId`, `userId`, `accountId`)
**Outputs:** service that:
- builds a `TradeProposal` with **one generated `idempotencyKey: UUID` per line**, `status = AWAITING_CONFIRMATION`, persisted as **session-scoped ADK state** keyed by `conversationId` (reusing the mechanism used by `ConversationSummaryService`), superseding any prior pending proposal
- reads the current pending proposal
- records per-line outcomes and transitions `CONFIRMED`
- marks `DECLINED` / `SUPERSEDED`
**Acceptance criteria:**
- [ ] Proposal stored as ADK session state — **no `@Entity`, no repository**
- [ ] `idempotencyKey` generated backend-side, one per line, preserved for reuse on execution
- [ ] At most one `AWAITING_CONFIRMATION` proposal per conversation (new proposal supersedes prior)
- [ ] Re-confirming an already-`CONFIRMED` proposal returns stored outcomes (no regeneration)
- [ ] Unit tests cover propose, supersede, confirm, decline, and the one-pending-proposal rule
**Depends on:** none

### [SVC-3] — `ProposeTradesTool` — agent tool to parse, store, and summarise a batch

**Layer:** SVC / agent tool (`agent.tool`)
**Domain:** agent
**Use case:** execute-trades-via-assistant
**Implements:** Flow A steps 3–5 (+ Flow A error cases)
**Inputs:** `ToolContext` (scope: `accountId`, `userId`) + LLM-supplied order lines (`[{ ticker, side, quantity }]`)
**Outputs:** `@Component ProposeTradesTool` exposing a `FunctionTool` (mirroring `PortfolioHoldingsTool`) that validates each line (`quantity > 0`, `side ∈ {BUY,SELL}`), omits/flags invalid lines, calls `TradeProposalService` to store the proposal, and returns a structured result (proposed lines + target account) for the LLM to present
**Acceptance criteria:**
- [ ] One `@Component` in `agent.tool`; `FunctionTool.create(...)` pattern
- [ ] Scope (`accountId`/`userId`) resolved from `ToolContext` and validated against the authenticated `userId` (reuse the `ToolScope` approach from `PortfolioHoldingsTool`)
- [ ] Invalid quantity handled per Flow A error cases (line not proposed)
- [ ] Unit tests: valid batch, invalid-quantity line, scope mismatch
**Depends on:** SVC-2

### [SVC-4] — `ExecuteTradesTool` — agent tool to execute a confirmed batch sequentially

**Layer:** SVC / agent tool (`agent.tool`)
**Domain:** agent
**Use case:** execute-trades-via-assistant
**Implements:** Flow B steps 3–5; Flow C steps 1–2; Flow D steps 1 & 4 (+ Flow D error cases); Flow C step 4 (refresh marker)
**Inputs:** `ToolContext` (scope); reads the stored pending `TradeProposal`
**Outputs:** `@Component ExecuteTradesTool` `FunctionTool` that: reads the pending proposal; if already `CONFIRMED` returns stored outcomes (idempotency); otherwise iterates lines **in order**, calling `StockTradingApi.placeMarketOrder(...)` with each line's **stored `idempotencyKey`** + scope; records each outcome (FILLED / REJECTED / error — catching per-line service exceptions and **continuing**); transitions the proposal `CONFIRMED`; returns a structured per-order report + aggregate counts
**Acceptance criteria:**
- [ ] Continues past a REJECTED result and past a per-line exception (Flow B step 5) — one failure never aborts the batch
- [ ] `DuplicateIdempotencyKeyException` mapped to an "already placed" outcome (Flow D duplicate case)
- [ ] Re-confirm returns stored outcomes without re-placing
- [ ] When the batch results in **≥1 FILLED** order, sets a transient session-state marker `pendingTradeRefreshAccountId = <conversation accountId>` (via `ToolContext`); a fully-rejected/failed batch sets no marker
- [ ] Returns a report the LLM renders (per-line status + "N of M filled")
- [ ] Unit tests: all filled; mixed filled/rejected; one line throws (batch continues); re-confirm returns stored outcomes; marker set only when ≥1 fill
**Depends on:** SVC-2, API-1 (compile) · SVC-1 (runtime impl)

### [SVC-5] — Register `tradeExecutorAgent` sub-agent and wire delegation

**Layer:** SVC / agent wiring (`config.AgentConfiguration`)
**Domain:** agent
**Use case:** execute-trades-via-assistant
**Implements:** `agent-assistant-chat` updated Agent Hierarchy + Delegation rule; Flow A step 2; Flow B step 2
**Inputs:** `agentModel: Gemini`, `ProposeTradesTool`, `ExecuteTradesTool`
**Outputs:**
- new `tradeExecutorAgent` `LlmAgent` bean with both tools and an instruction encoding: MARKET only; parse into a batch; propose and require **one** confirmation; on confirm execute one-by-one; **continue on failure**; report each outcome
- add `tradeExecutorAgent` to `mainAgent.subAgents(...)`
- update the `mainAgent` instruction to delegate trade-execution requests to `trade-executor` and never place trades itself
**Acceptance criteria:**
- [ ] `mainAgent` wires **both** `portfolio-analyst` and `trade-executor`; existing portfolio-analyst wiring unchanged
- [ ] `mainAgent` instruction routes trade requests to the trade executor
- [ ] No change to `agent-openapi.yaml` request/response schema beyond the SSE event (API-CONTRACT-1)
- [ ] `AgentConfigurationTest` extended to assert the trade-executor sub-agent is present and wired
**Depends on:** SVC-3, SVC-4

### [SVC-6] — Emit a structured `trade-executed` signal into the agent stream

**Layer:** SVC (`agent.service`)
**Domain:** agent
**Use case:** execute-trades-via-assistant
**Implements:** agent-execute-trades Flow C step 4 (detection source of truth)
**Inputs:** the per-turn ADK event stream; the `pendingTradeRefreshAccountId` session-state marker set by SVC-4
**Outputs:** introduce a sealed `AgentStreamEvent` (`TextChunk(text)` | `TradeExecuted(accountId)`); change `AgentService.query()` to return `Flowable<AgentStreamEvent>`; existing text chunks become `TextChunk`; on turn completion, if the marker is present, append one `TradeExecuted(accountId)` event and clear the marker
**Acceptance criteria:**
- [ ] Reply text is unchanged and still de-duplicated exactly as today (partial/aggregate handling preserved)
- [ ] Exactly one `TradeExecuted` event is appended per turn that filled ≥1 order; none otherwise
- [ ] `accountId` comes from session scope, never from the model's text
- [ ] Marker is cleared after emission so the next turn starts clean
- [ ] Unit tests: trade turn emits one `TradeExecuted`; plain chat turn emits none; fully-rejected batch emits none
**Depends on:** SVC-4

---

## Agent domain — CONTROLLER

### [CONTROLLER-1] — Render `AgentStreamEvent` over SSE

**Layer:** CONTROLLER (`agent.controller`)
**Domain:** agent
**Use case:** execute-trades-via-assistant
**Implements:** agent-execute-trades Flow C step 4 (transport)
**Inputs:** `Flowable<AgentStreamEvent>` from `AgentService`
**Outputs:** `AgentApiDelegateImpl.streamReply` renders `TextChunk` as the existing `data:` event and `TradeExecuted` as `event: trade-executed\ndata: {"accountId":"<uuid>"}`
**Acceptance criteria:**
- [ ] `TextChunk` output byte-identical to today's `data:` framing
- [ ] `TradeExecuted` emitted as a named SSE event with a JSON `{accountId}` payload
- [ ] Buffered (non-SSE) fallback remains text-only — documented limitation: the refresh signal is not delivered on that path
- [ ] `@SpringBootTest` + MockMvc test asserts the named event appears in the SSE body for a trade turn and is absent for a plain turn
**Depends on:** SVC-6

---

## Agent domain — API-CONTRACT

### [API-CONTRACT-1] — Document the `trade-executed` SSE event in the agent contract

**Layer:** API-CONTRACT (`services/contract/agent-openapi.yaml`)
**Domain:** agent
**Use case:** execute-trades-via-assistant
**Implements:** agent-execute-trades Flow C step 4
**Inputs:** the named SSE event introduced by CONTROLLER-1 (`event: trade-executed`, payload `{ accountId: uuid }`) on the existing `POST /api/v1/agent/query` streaming response
**Outputs:** updated `agent-openapi.yaml` describing the structured stream event / payload schema on the existing operation (no new path, no new operation)
**Acceptance criteria:**
- [ ] The `trade-executed` event and its `{accountId}` schema are documented on the `/agent/query` streaming response
- [ ] No new path or operation added; `info.title` and existing paths untouched
**Depends on:** CONTROLLER-1

---

## Frontend (agent domain) — CLI

### [CLI-1] — Parse the `trade-executed` SSE event in the agent client

**Layer:** CLI (`services/front-end/src/domains/agent/api/queryAgent.ts`)
**Domain:** agent (frontend)
**Use case:** execute-trades-via-assistant
**Implements:** agent-execute-trades Flow C step 4
**Inputs:** SSE blocks that may carry `event: trade-executed` with a `data: {"accountId"}` payload
**Outputs:** `flushSseBuffer` reads the `event:` field; `queryAgent` gains an `onTradeExecuted(accountId: string)` callback; `trade-executed` blocks are routed to it and **not** to `onToken`
**Acceptance criteria:**
- [ ] `trade-executed` payload parsed and surfaced via `onTradeExecuted`; never appended to the transcript
- [ ] Default `data:` token behaviour unchanged for normal replies and the `error` event
- [ ] Vitest coverage: a stream with a `trade-executed` event fires `onTradeExecuted` once with the accountId and emits no stray token
**Depends on:** API-CONTRACT-1

---

## Frontend (agent domain) — STATE

### [STATE-1] — Invalidate balance & holdings caches from the parsed signal

**Layer:** STATE (`services/front-end/src/domains/agent/hooks`)
**Domain:** agent (frontend)
**Use case:** execute-trades-via-assistant
**Implements:** agent-execute-trades Flow C step 4
**Inputs:** `onTradeExecuted(accountId)` from CLI-1
**Outputs:** `useAgentChat` passes an `onTradeExecuted` handler that invalidates the accounts query key (`?...&status=active`) and the portfolio holdings key (`?accountId=<accountId>`) — the same keys the manual buy/sell flows invalidate
**Acceptance criteria:**
- [ ] Invalidation fires only on a `trade-executed` signal, scoped to the signalled `accountId`
- [ ] Plain chat turns never invalidate
- [ ] `renderHook` test: a `trade-executed` signal invalidates both keys; a plain turn invalidates neither
**Depends on:** CLI-1

---

## Dependency summary

| Task | Title | Depends on |
|------|-------|------------|
| API-1 | `StockTradingApi` interface + result types | none |
| SVC-1 | `StockTradingApiAdapter` | API-1 |
| SVC-2 | `TradeProposalService` (ADK state) | none |
| SVC-3 | `ProposeTradesTool` | SVC-2 |
| SVC-4 | `ExecuteTradesTool` (+ refresh marker) | SVC-2, API-1 |
| SVC-5 | Register `tradeExecutorAgent` + delegation | SVC-3, SVC-4 |
| SVC-6 | Emit `TradeExecuted` stream signal | SVC-4 |
| CONTROLLER-1 | Render `AgentStreamEvent` over SSE | SVC-6 |
| API-CONTRACT-1 | Document `trade-executed` in `agent-openapi.yaml` | CONTROLLER-1 |
| CLI-1 | Parse `trade-executed` in `queryAgent.ts` | API-CONTRACT-1 |
| STATE-1 | Invalidate caches from parsed signal | CLI-1 |

**Build order:** API-1 → SVC-1 → SVC-2 → SVC-3 → SVC-4 → SVC-5 → SVC-6 → CONTROLLER-1 → API-CONTRACT-1 → CLI-1 → STATE-1.
