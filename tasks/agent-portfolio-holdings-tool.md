# Tasks — Portfolio Analyst Holdings Tool

**Source issue:** #196 — Provide agent with Portfolio tool
**Use case:** `domain/usecases/chat-with-ai-assistant.md` (extension — Flow E: Portfolio Analyst Holdings Tool Call)
**Decision:** `decisions/2026-09-27-portfolio-analyst-holdings-tool.md`
**Scope:** Backend only. No frontend, no OpenAPI/REST change, no controller change.

---

## Use case summary

When the Main Agent delegates a portfolio query, the Portfolio Analyst sub-agent invokes a **holdings tool** scoped to the conversation's single `accountId` and the JWT-resolved `userId`, retrieves priced holdings/cash/unrealised P&L via the Portfolio `api/` interface (reusing `PortfolioQueryService.getHoldings` — no duplicated logic), and grounds its reply. On tool failure (account not owned / not found / price or balance unavailable) it surfaces the error to the user.

- **Actor:** Portfolio Analyst sub-agent (system), acting on behalf of the authenticated user during a chat turn.
- **Models:** `AgentConversation` (supplies `userId`/`accountId` scope from ADK session state), `Position` (read via `PortfolioApi`).
- **Events:** none.

## Grounding notes, defaults & decisions

- **Verified, not assumed:** `AgentService.loadOrCreateSession` already writes `state["userId"]` and `state["accountId"]` into the ADK session, so the tool reads scope from `ToolContext` — **no change to `AgentService` is required.**
- **Architecture (service / api / mapping separation):** the service layer keeps its own result types and logic unchanged; the `api/` package exposes its **own** model; the controller keeps its existing service→REST mapping unchanged; a dedicated **service→api mapper** feeds the `api/` interface. No type relocation.
- **Cross-domain rule:** consumers may import only from `{domain}.api`. The agent tool therefore consumes the `portfolio.api` model only — never `portfolio.service`/`portfolio.model`.
- **Default (flagged):** the api holdings model exposes holdings + cash + unrealised P&L only; chart insight aggregations are excluded (the tool doesn't need them). Adjust if grounding should include insights.
- **No API-CONTRACT task:** `PortfolioApi` is an internal Kotlin cross-domain interface, not a REST surface. The `/api/v1/agent/query` OpenAPI contract is unchanged.
- **No-work layers:** DB, REPO, EXCEPTION (existing portfolio exceptions reused), EVT, CONTROLLER (unchanged), and all frontend layers (CLI/STATE/COMP/SCREEN).

---

## Tasks

### [API-1] — Add the Portfolio `api/` holdings model and interface method

**Layer:** API (`portfolio.api`)
**Domain:** portfolio
**Use case:** chat-with-ai-assistant
**Implements:** agent-assistant-chat Flow E — steps 2–4 (the cross-domain read contract the tool calls)
**Inputs:** existing `PortfolioApi` interface (`getPositionQuantity`).
**Outputs:**
- New **api-owned** model in `portfolio.api` (independent of the service result types) — e.g. `PortfolioHoldingsView` carrying per-stock `ticker, quantity, currentPrice, currentValue, avgPrice, unrealisedPnL, portfolioPercent` and a cash `balance, currency`.
- New method: `PortfolioApi.getHoldings(accountId: UUID, userId: UUID): PortfolioHoldingsView`.

**Acceptance criteria:**
- [ ] The api model is defined entirely in `portfolio.api`; it does not reference `portfolio.service` result types.
- [ ] `getHoldings(accountId, userId)` is declared on `PortfolioApi` returning the api model.
- [ ] Insights/chart aggregations are excluded (holdings + cash + unrealised P&L only).
- [ ] `getPositionQuantity` retained unchanged.

**Depends on:** none

---

### [SVC-1] — Implement `PortfolioApi.getHoldings` by reusing service logic + a service→api mapper

**Layer:** Service (`portfolio.service`)
**Domain:** portfolio
**Use case:** chat-with-ai-assistant
**Implements:** agent-assistant-chat Flow E — step 3 (ownership, load positions, prices, cash, compute) and its error cases
**Inputs:** existing `PortfolioQueryService.getHoldings(accountId, userId): PortfolioHoldingsResult` (unchanged); the api model + interface method from API-1.
**Outputs:**
- A dedicated service→api **mapper** converting `PortfolioHoldingsResult` → `PortfolioHoldingsView`.
- A `PortfolioApi.getHoldings` implementation that calls the **existing** service logic and maps the result. Hosted so it does not collide with the existing same-named service method (a dedicated api-impl/adapter class, since two methods differing only by return type cannot co-exist on one class).

**Acceptance criteria:**
- [ ] The existing holdings computation in `PortfolioQueryService` is not modified or duplicated — the api path delegates to it.
- [ ] A distinct mapper performs the service→api conversion (the api model is not built inline in the computation).
- [ ] Exactly one Spring bean implements `PortfolioApi`.
- [ ] Ownership (`PortfolioAccountAccessDenied`/`NotFound`) and price/balance (`PortfolioPrice`/`BalanceUnavailable`) exceptions propagate unchanged.
- [ ] `PortfolioQueryServiceTest` and the controller are untouched; a new unit test covers the service→api mapping.

**Depends on:** API-1

---

### [SVC-2] — Create the Portfolio holdings agent tool

**Layer:** Service (agent domain — `agent.tool` / `agent.service`)
**Domain:** agent
**Use case:** chat-with-ai-assistant
**Implements:** agent-assistant-chat Flow E — steps 1–5 and all four error cases
**Inputs:**
- `PortfolioApi` (imported from `portfolio.api` only).
- ADK `ToolContext` session state carrying `userId` and `accountId` (already written by `AgentService.loadOrCreateSession`).

**Outputs:** an ADK `FunctionTool`-compatible tool that resolves `userId`/`accountId` from session state, calls `PortfolioApi.getHoldings(accountId, userId)`, returns a model-relayable holdings summary, and returns an error payload on failure.

**Acceptance criteria:**
- [ ] `accountId` and `userId` are read from ADK tool/session context — never from model-supplied tool arguments.
- [ ] Calls `PortfolioApi.getHoldings(accountId, userId)`; imports only from `portfolio.api` (no `portfolio.model`/`.service`/repository/DB access).
- [ ] On success returns holdings + cash + unrealised P&L in a structure the LLM can relay (default: full api model).
- [ ] Catches `PortfolioAccountNotFoundException`, `PortfolioAccountAccessDeniedException`, `PortfolioPriceUnavailableException`, `PortfolioBalanceUnavailableException` and returns an error result so the Analyst surfaces the message to the user (Flow E error cases).
- [ ] No direct repository/DB access (per the standing api/-only tool-access rule).
- [ ] Unit tests (KoTest + mockito-kotlin): happy path returns holdings; each of the four exceptions yields a surfaced error result; scope is taken from session context, not tool args.

**Depends on:** API-1, SVC-1

---

### [SVC-3] — Register the tool on the Portfolio Analyst and update its instruction

**Layer:** Service — agent runtime wiring (`config/AgentConfiguration.kt`)
**Domain:** agent
**Use case:** chat-with-ai-assistant
**Implements:** agent-assistant-chat Agent Hierarchy + Flow B step 7 / Flow E step 1 (Portfolio Analyst now holds the holdings tool)
**Inputs:** the `portfolioAnalystAgent` `LlmAgent` bean; the tool from SVC-2.
**Outputs:** `portfolioAnalystAgent` built with `.tools(<holdings tool>)` and a revised instruction.

**Acceptance criteria:**
- [ ] The holdings tool is registered on the Portfolio Analyst only (not the Main Agent).
- [ ] Instruction updated to: call the tool to read the user's real holdings for the conversation's single account, and surface tool errors to the user; the "Use no tools" / "do not have access to live … data" wording is removed.
- [ ] Main Agent still delegates all portfolio queries to the Portfolio Analyst (`.subAgents` unchanged); Main Agent gets no tool.
- [ ] Application context loads and `./gradlew build` passes with the tool wired.

**Depends on:** SVC-2

---

## Dependency summary

| Task | Title | Depends on |
|------|-------|-----------|
| API-1 | Add Portfolio `api/` holdings model + interface method | none |
| SVC-1 | Implement `getHoldings` via reuse + service→api mapper | API-1 |
| SVC-2 | Create the Portfolio holdings agent tool | API-1, SVC-1 |
| SVC-3 | Register tool on Portfolio Analyst + instruction | SVC-2 |

**Execution order:** API-1 → SVC-1 → SVC-2 → SVC-3.
