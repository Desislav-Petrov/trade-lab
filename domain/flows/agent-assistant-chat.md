# Agent Assistant Chat

## Overview

Covers the Trade Lab AI Assistant — a chat widget docked at the bottom of every page and available on the whole platform to logged-in users only. Clicking it opens a chat popup where the user can hold a multi-turn conversation with the platform's agent. Each user message is sent to a single backend endpoint (`POST /api/v1/agent/query`) which forwards it to a Main orchestration agent built on Google ADK for Java. The Main Agent delegates portfolio-related questions to a Portfolio Analyst sub-agent. The agent's reply is streamed back to the popup token-by-token via Server-Sent Events (ADK `StreamingMode.SSE`).

The Portfolio Analyst is granted a single tool — a **portfolio holdings tool** — that lets it read the authenticated user's real priced holdings, cash, and unrealised P&L for the account carried on the conversation. It calls this tool to ground its answers in real data, scoped to one account at a time. Trade-fill history and cross-account reasoning are not exposed. The tool is backed by the Portfolio domain's `api/` interface (the same read path as `GET /api/v1/portfolio/holdings`); no logic is duplicated for the agent. See `decisions/2026-09-27-portfolio-analyst-holdings-tool.md`, which supersedes the no-tools stance of `decisions/2026-09-21-agent-query-streaming-endpoint.md`.

Conversation context is held server-side by the ADK in-memory session, keyed by a frontend-generated `conversationId`. See `domain/model/agent-conversation.md`.

---

## Agent Hierarchy

The `agent` domain defines a two-level agent hierarchy. This structure is the spine of the feature and is designed to grow — future sub-agents are added under the Main Agent without changing the endpoint or the widget.

- **Main Agent (orchestrator)**: The single entry point invoked by `AgentService`. It receives the user message plus prior conversation context and decides how to route it. **The Main Agent must not itself perform portfolio analysis.** It delegates portfolio-related queries to the Portfolio Analyst sub-agent and streams the sub-agent's reply back to the caller.
- **Portfolio Analyst (sub-agent)**: Specialises in analysing portfolio performance and answering questions about the user's holdings. It is the first and only sub-agent in this feature. It is granted a single **portfolio holdings tool**, backed by the Portfolio domain's `api/` interface, which returns the priced holdings, cash, and unrealised P&L for the conversation's `accountId`. It is scoped to that one account and to holdings only — it has no access to trade-fill history or to the user's other accounts.

### Delegation rule

- Portfolio-related queries are always handled by the Portfolio Analyst, never by the Main Agent directly.
- Non-portfolio queries are handled by the Main Agent per its own instructions (there is no other sub-agent yet).

---

## Flow A — Open and Close the Assistant Widget

The user opens or closes the global assistant popup. The widget is only rendered for authenticated users.

### Actors

- **Authenticated User**: A logged-in user on any page of the platform.
- **Guest Browser**: The React frontend rendering the docked widget and popup, and holding the in-session transcript.

### Preconditions

- The user has an active session (is logged in). The widget is not rendered for logged-out users or on pre-login pages.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest Browser | Render docked widget | While a session exists, renders the "Trade Lab AI Assistant" widget docked at the bottom of every page. |
| 2 | Authenticated User | Click the widget | Clicks the docked widget to open the chat popup. |
| 3 | Guest Browser | Open popup | Opens the chat popup. If a transcript already exists in frontend session state, it is rendered. Otherwise the popup shows an empty conversation. |
| 4 | Authenticated User | Click close | Closes the popup. |
| 5 | Guest Browser | Hide popup, retain transcript | Hides the popup but retains the transcript and `conversationId` in frontend session state so reopening resumes the same conversation. |

### Postconditions

- The popup is open or closed per the user's action.
- The transcript and `conversationId` persist in frontend session state across open/close and page navigation while the session lives.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| User is logged out | No active session | The widget is not rendered at all. |

---

## Flow B — Send a Message and Stream the Reply

The user sends a message. The frontend calls the agent endpoint; the Main Agent handles or delegates it; the reply streams back via SSE.

### Actors

- **Authenticated User**: A logged-in user typing into the popup.
- **Guest Browser**: The React frontend sending the query and rendering the streamed reply.
- **System (Agent)**: The `agent` backend — `AgentApiDelegateImpl`, `AgentService`, and the ADK Main Agent + Portfolio Analyst sub-agent.

### Preconditions

- The popup is open (Flow A) and the user has an active session.
- An account is selected in frontend state (the `portfolio` slice's selected account).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Type and send a message | Enters a message and submits it in the popup. |
| 2 | Guest Browser | Resolve conversationId | If this is the first message of the conversation, generates a new `conversationId` (UUID). Otherwise reuses the existing one. |
| 3 | Guest Browser | Append user turn to transcript | Immediately renders the user's message in the popup and shows a streaming placeholder for the reply. |
| 4 | Guest Browser | Call agent endpoint | Calls `POST /api/v1/agent/query` with body `{ accountId, conversationId, message }`. The selected `accountId` is read from the `portfolio` slice. The JWT is attached via the shared Axios interceptor. The request accepts a Server-Sent Events stream. |
| 5 | System (Agent) | Resolve userId | The controller resolves `userId` from the JWT (`SecurityContextHolder`), never from the body. |
| 6 | System (Agent) | Forward to agent | `AgentService` forwards the message to the ADK Main Agent for the session keyed by `conversationId`, creating the session on first use. It performs no business logic of its own beyond this forwarding. |
| 7 | System (Agent) | Route or delegate | The Main Agent inspects the message. Portfolio-related queries are delegated to the Portfolio Analyst sub-agent; other queries are handled by the Main Agent directly. The Main Agent never performs portfolio analysis itself. When the delegated query needs the user's real holdings, the Portfolio Analyst invokes its holdings tool — see Flow E. |
| 8 | System (Agent) | Stream reply (SSE) | The agent reply is streamed back to the caller token-by-token using ADK `StreamingMode.SSE`. The backend bridges the ADK `Flowable<Event>` to an SSE HTTP response. |
| 9 | Guest Browser | Render streamed tokens | Appends streamed tokens into the reply placeholder in real time as they arrive. |
| 10 | Guest Browser | Finalise reply turn | On stream completion, finalises the agent reply in the transcript held in frontend session state. |

### Postconditions

- The user message and the full agent reply are appended to the conversation, both server-side (ADK session) and in the frontend transcript.
- The reply was delivered incrementally via SSE.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Unauthenticated request | No valid JWT | System returns HTTP 401. Frontend redirects to `/login`. |
| No account selected | The `portfolio` slice has no selected account | Frontend blocks the send and prompts the user to select an account first. No request is made. |
| Stream fails mid-reply | The SSE stream errors or drops after starting | The popup shows a partial reply plus an inline error: "The assistant response was interrupted. Please try again." The user may resend. |
| Agent/backend error | The endpoint returns a non-2xx before streaming | The popup shows: "The assistant is unavailable. Please try again." The user's message remains in the transcript so it can be resent. |
| Streaming unavailable | SSE cannot be established | Fallback to a single buffered reply rendered at once (no token-by-token effect). |
| Holdings tool fails | The Portfolio Analyst's tool call errors | The Analyst surfaces the error to the user rather than answering from general knowledge — see Flow E. |

---

## Flow C — Continue a Multi-Turn Conversation

The user sends further messages in the same open conversation. Prior context is preserved server-side via the ADK session.

### Actors

- **Authenticated User**: A logged-in user continuing the conversation.
- **Guest Browser**: The React frontend reusing the existing `conversationId`.
- **System (Agent)**: The `agent` backend and its ADK session.

### Preconditions

- Flow B has completed at least once, so a `conversationId` and an ADK session exist.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Send another message | Types a follow-up message in the same popup. |
| 2 | Guest Browser | Reuse conversationId | Sends `POST /api/v1/agent/query` with the same `conversationId` as prior turns. |
| 3 | System (Agent) | Load session context | ADK loads the existing in-memory session for that `conversationId`, so the Main Agent sees prior turns and can answer in context. |
| 4 | System (Agent) | Route or delegate and stream | Same routing/delegation and SSE streaming as Flow B, steps 7–8, including the Portfolio Analyst holdings tool call (Flow E) when the query needs real holdings. |
| 5 | Guest Browser | Append to transcript | Renders the streamed reply and appends the new turn to the frontend transcript. |

### Postconditions

- The new turn is appended to the same conversation; the agent's answer reflects earlier turns.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Backend restarted since last turn | ADK session for `conversationId` no longer exists | The backend treats it as a new conversation with empty context. The reply may not reflect earlier turns. No error is surfaced. |

---

## Flow D — Transcript Persistence and Loss

Defines how the conversation transcript lives on the frontend and when it is lost.

### Actors

- **Authenticated User**: A logged-in user across page navigations and reloads.
- **Guest Browser**: The React frontend holding the transcript in session state.

### Preconditions

- At least one message has been exchanged (Flow B).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Navigate between pages | Moves around the platform with the widget available on every page. |
| 2 | Guest Browser | Retain transcript | The transcript and `conversationId` persist in frontend session state across navigation and popup open/close. |
| 3 | Authenticated User | Reload the page or restart the browser | Triggers a fresh frontend load. |
| 4 | Guest Browser | Discard transcript | The frontend transcript and `conversationId` are cleared. The next message starts a brand-new conversation with a new `conversationId`. |

### Postconditions

- The transcript survives navigation and open/close within a running frontend session.
- The transcript is lost on frontend reload/restart; a subsequent message begins a new conversation.

---

## Flow E — Portfolio Analyst Holdings Tool Call

When a delegated portfolio query needs the user's real data, the Portfolio Analyst invokes its holdings tool. The tool reads the priced holdings for the conversation's account via the Portfolio `api/` interface and returns them to the agent so the reply is grounded in real data.

### Actors

- **System (Agent)**: The Portfolio Analyst sub-agent and its registered holdings tool.
- **System (Portfolio)**: The Portfolio backend service, reached via its `api/` interface — the same read path that backs `GET /api/v1/portfolio/holdings`.

### Preconditions

- The Main Agent has delegated a portfolio-related query to the Portfolio Analyst (Flow B step 7, or Flow C).
- The conversation carries an `accountId` and a JWT-resolved `userId`.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Agent) | Decide to call the tool | The Portfolio Analyst determines the query needs the user's real holdings and invokes the portfolio holdings tool. |
| 2 | System (Agent) | Supply scope | The tool is called with the conversation's `accountId` and the JWT-resolved `userId`. The agent cannot substitute a different account or user. |
| 3 | System (Portfolio) | Enforce ownership and load holdings | The Portfolio `api/` interface delegates to the same service backing `GET /api/v1/portfolio/holdings`. It verifies the account belongs to `userId`, loads positions, fetches live prices (Market Data `api/`) and cash (Ledger `api/`), and computes priced holdings and unrealised P&L. No logic is duplicated for the agent. |
| 4 | System (Portfolio) | Return holdings | Returns the priced holdings and cash for the account to the tool. |
| 5 | System (Agent) | Ground the reply | The Portfolio Analyst incorporates the returned holdings into its answer and streams the reply back (Flow B step 8). |

### Postconditions

- The Portfolio Analyst's reply reflects the user's real holdings for the conversation's account.
- No portfolio data is persisted in the `agent` domain; the tool result is used only for the current reply.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Account not owned by user | Resolved account's `userId` ≠ conversation `userId` | The tool returns an ownership error. The Portfolio Analyst surfaces it to the user (e.g. "I couldn't access that account's portfolio.") rather than answering from general knowledge. |
| Account not found | `accountId` does not resolve | The tool returns not-found. The Analyst tells the user the account's portfolio could not be found. |
| Price or balance data unavailable | The Market Data or Ledger call fails | The tool returns a data-unavailable error. The Analyst tells the user it couldn't retrieve the portfolio right now and to try again. |
| No holdings | The account has no stock positions and no cash | The tool returns empty holdings. The Analyst answers based on an empty (or cash-only) portfolio; no error is surfaced. |

---

## Query Request Specification

`POST /api/v1/agent/query`

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| accountId | uuid | yes | The account selected in frontend state. Carried on every request and consumed by the Portfolio Analyst's holdings tool to scope portfolio retrieval to this account. |
| conversationId | uuid | yes | Frontend-generated conversation key. New on the first turn; reused on every subsequent turn. |
| message | string | yes | The user's message text. |

- `userId` is resolved server-side from the JWT and is never present in the request body. It is also the `userId` supplied to the holdings tool.
- The response is a Server-Sent Events stream of reply tokens (ADK `StreamingMode.SSE`). If streaming cannot be established, a single buffered reply is returned instead.

---

## Domain Models Involved

- **AgentConversation**: Created on the first query for a `conversationId` and appended to on every subsequent turn. Held in the ADK in-memory session; never persisted. Its `accountId` scopes the Portfolio Analyst's holdings tool call. See `domain/model/agent-conversation.md`.
- **Position**: Read by the holdings tool via the Portfolio `api/` interface (the same read path as `GET /api/v1/portfolio/holdings`). Fields used: `ticker`, `quantity`, `avgPrice`, plus live `currentPrice` and computed unrealised P&L. Never shared as an entity across domains; the `agent` domain consumes only the tool result. See `domain/model/position.md`.
- **Session**: `userId` is resolved server-side from the JWT (see `domain/flows/jwt-authentication.md`) and is the identity passed to the holdings tool. The frontend `accessToken` is attached via the shared Axios interceptor. The widget only renders while a frontend session exists.
- **Account**: The selected `accountId` is read from the `portfolio` Zustand slice and sent with each query. It scopes the holdings tool to a single account. The `agent` domain stores the UUID only; no `Account` entity is shared across domains.
