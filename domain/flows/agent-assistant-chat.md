# Agent Assistant Chat

## Overview

Covers the Trade Lab AI Assistant — a chat widget docked at the bottom of every page and available on the whole platform to logged-in users only. Clicking it opens a chat popup where the user can hold a multi-turn conversation with the platform's agent. Each user message is sent to a single backend endpoint (`POST /api/v1/agent/query`) which forwards it to a Main orchestration agent built on Google ADK for Java. The Main Agent delegates portfolio-related questions to a Portfolio Analyst sub-agent. The agent's reply is streamed back to the popup token-by-token via Server-Sent Events (ADK `StreamingMode.SSE`).

The Portfolio Analyst is granted a single tool — a **portfolio holdings tool** — that lets it read the authenticated user's real priced holdings, cash, and unrealised P&L for the account carried on the conversation. It calls this tool to ground its answers in real data, scoped to one account at a time. Trade-fill history and cross-account reasoning are not exposed. The tool is backed by the Portfolio domain's `api/` interface (the same read path as `GET /api/v1/portfolio/holdings`); no logic is duplicated for the agent. See `decisions/2026-09-27-portfolio-analyst-holdings-tool.md`, which supersedes the no-tools stance of `decisions/2026-09-21-agent-query-streaming-endpoint.md`.

The verbatim conversation context is held server-side by the ephemeral ADK session, keyed by a frontend-generated `conversationId`. Durable continuity across restarts, reloads, and new conversations is provided separately by a user-scoped `ConversationSummary` (`last_conversation`): the agent regenerates it after every turn and presents it as an opening summary when the user returns. There is one active conversation per user — the platform does not track multiple conversations. See `domain/model/agent-conversation.md`, `domain/model/conversation-summary.md`, and `decisions/2026-10-10-agent-conversation-summary-continuity.md`.

The assistant also greets the user **by name**. The user's first name is resolved via a durable, user-scoped `AgentUserProfile` (`user_profile`) and, on a cache miss, fetched from the User domain's `api/` interface and cached for future sessions. The name is injected into the Main Agent's context so it can address the user by name throughout the conversation, and it is presented as the opening greeting whenever the assistant opens onto an empty conversation with no summary to show. See `domain/model/agent-user-profile.md`, Flow I, and `decisions/2026-10-17-agent-personalised-name-greeting.md`.

---

## Agent Hierarchy

The `agent` domain defines a two-level agent hierarchy. This structure is the spine of the feature and is designed to grow — future sub-agents are added under the Main Agent without changing the endpoint or the widget.

- **Main Agent (orchestrator)**: The single entry point invoked by `AgentService`. It receives the user message plus prior conversation context and decides how to route it. **The Main Agent must not itself perform portfolio analysis.** It delegates portfolio-related queries to the Portfolio Analyst sub-agent and streams the sub-agent's reply back to the caller. The Main Agent is additionally provided the user's first name (resolved via the `AgentUserProfile` user-scoped state — Flow I) so it can greet and address the user by name throughout the conversation.
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
| 3 | Guest Browser | Open popup | Opens the chat popup. If a transcript already exists in frontend session state, it is rendered. Otherwise the popup requests the opening greeting: the summary greeting (Flow G) when a summary exists, or the name greeting (Flow I) when it does not. |
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

The user sends a message. The frontend calls the agent endpoint; the Main Agent handles or delegates it; the reply streams back via SSE; the backend then refreshes the durable summary.

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
| 6 | System (Agent) | Forward to agent | `AgentService` forwards the message to the ADK Main Agent for the session keyed by `conversationId`, creating the session on first use. It performs no business logic of its own beyond this forwarding. The Main Agent has the user's name available (Flow I) so it may address the user by name. |
| 7 | System (Agent) | Route or delegate | The Main Agent inspects the message. Portfolio-related queries are delegated to the Portfolio Analyst sub-agent; other queries are handled by the Main Agent directly. The Main Agent never performs portfolio analysis itself. When the delegated query needs the user's real holdings, the Portfolio Analyst invokes its holdings tool — see Flow E. |
| 8 | System (Agent) | Stream reply (SSE) | The agent reply is streamed back to the caller token-by-token using ADK `StreamingMode.SSE`. The backend bridges the ADK `Flowable<Event>` to an SSE HTTP response. |
| 9 | Guest Browser | Render streamed tokens | Appends streamed tokens into the reply placeholder in real time as they arrive. |
| 10 | Guest Browser | Finalise reply turn | On stream completion, finalises the agent reply in the transcript held in frontend session state. |
| 11 | System (Agent) | Regenerate conversation summary | After the reply completes, the backend regenerates the user-scoped `ConversationSummary` (`last_conversation`) to reflect the latest turn and persists it via the PostgreSQL-backed ADK `SessionService`. See Flow H. |

### Postconditions

- The user message and the full agent reply are appended to the conversation (ephemeral ADK session) and to the frontend transcript.
- The reply was delivered incrementally via SSE.
- The user-scoped `ConversationSummary` is updated to reflect this turn, so continuity survives a subsequent restart or reload.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Unauthenticated request | No valid JWT | System returns HTTP 401. Frontend redirects to `/login`. |
| No account selected | The `portfolio` slice has no selected account | Frontend blocks the send and prompts the user to select an account first. No request is made. |
| Stream fails mid-reply | The SSE stream errors or drops after starting | The popup shows a partial reply plus an inline error: "The assistant response was interrupted. Please try again." The user may resend. |
| Agent/backend error | The endpoint returns a non-2xx before streaming | The popup shows: "The assistant is unavailable. Please try again." The user's message remains in the transcript so it can be resent. |
| Streaming unavailable | SSE cannot be established | Fallback to a single buffered reply rendered at once (no token-by-token effect). |
| Summary regeneration fails | The step 11 summary write fails | The reply is unaffected and is still delivered. The summary retains its previous value; continuity falls back to the prior turn's summary. No error is surfaced to the user. |
| Holdings tool fails | The Portfolio Analyst's tool call errors | The Analyst surfaces the error to the user rather than answering from general knowledge — see Flow E. |

---

## Flow C — Continue a Multi-Turn Conversation

The user sends further messages in the same open conversation. While the backend keeps running, prior verbatim context is preserved via the ADK session; after a restart, continuity is recovered from the summary.

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
| 3 | System (Agent) | Load session context | ADK loads the existing session for that `conversationId`, so the Main Agent sees prior turns and can answer in context. |
| 4 | System (Agent) | Route or delegate and stream | Same routing/delegation and SSE streaming as Flow B, steps 7–8, including the Portfolio Analyst holdings tool call (Flow E) when the query needs real holdings. |
| 5 | Guest Browser | Append to transcript | Renders the streamed reply and appends the new turn to the frontend transcript. |
| 6 | System (Agent) | Regenerate conversation summary | As in Flow B step 11, the user-scoped summary is refreshed after the reply. |

### Postconditions

- The new turn is appended to the same conversation; the agent's answer reflects earlier turns.
- The user-scoped `ConversationSummary` reflects the latest turn.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Backend restarted since last turn | The verbatim ADK session for `conversationId` no longer exists | The verbatim history is gone, but continuity is recovered from the user-scoped `ConversationSummary`: on the next open the agent presents the summary (Flow G). Earlier turns are reflected in summarised form, not verbatim. No error is surfaced. |

---

## Flow D — Transcript Persistence and Loss

Defines how the conversation transcript lives on the frontend, when the verbatim history is lost, and how the summary recovers continuity.

### Actors

- **Authenticated User**: A logged-in user across page navigations and reloads.
- **Guest Browser**: The React frontend holding the transcript in session state.
- **System (Agent)**: The `agent` backend holding the durable user-scoped summary.

### Preconditions

- At least one message has been exchanged (Flow B).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Navigate between pages | Moves around the platform with the widget available on every page. |
| 2 | Guest Browser | Retain transcript | The transcript and `conversationId` persist in frontend session state across navigation and popup open/close. |
| 3 | Authenticated User | Reload the page or restart the browser | Triggers a fresh frontend load. |
| 4 | Guest Browser | Discard verbatim transcript | The frontend transcript and `conversationId` are cleared. The verbatim history is gone. |
| 5 | Guest Browser | Recover continuity via summary | On the next open, the agent presents the durable user-scoped summary of the previous conversation (Flow G). The next user message begins a fresh `conversationId`, but the agent continues from the summary rather than from scratch. |

### Postconditions

- The verbatim transcript survives navigation and open/close within a running frontend session.
- The verbatim transcript is lost on frontend reload/restart, but the agent recovers continuity from the durable summary on the next open.

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

## Flow F — Start a New Conversation

The user explicitly starts a fresh conversation via a "New conversation" button in the popup. The previous conversation becomes unreachable and the popup is cleared of all prior context. Starting a new conversation always greets the user **by name** (Flow I); the carried-forward **summary** is **not** re-presented here (see `decisions/2026-10-18-new-conversation-name-greeting.md`, superseding `decisions/2026-10-17-new-conversation-blank-slate.md`). The durable summary still persists on the backend and carries forward: it is surfaced again the next time the user *opens* the assistant onto an empty conversation (e.g. after a page reload or backend restart), per Flow G, until the new conversation's first turn overwrites it.

### Actors

- **Authenticated User**: A logged-in user who wants to start over.
- **Guest Browser**: The React frontend resetting conversation state.
- **System (Agent)**: The `agent` backend holding the durable user-scoped summary and the user-scoped `AgentUserProfile`.

### Preconditions

- The assistant popup is open. A conversation may or may not be in progress.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click "New conversation" | Clicks the "New conversation" button in the popup. |
| 2 | Guest Browser | Reset conversation state | Discards the in-memory transcript and the current `conversationId`. The previous conversation becomes unreachable — there is no way back to it. |
| 3 | Guest Browser | Greet by name on a cleared view | The popup is cleared of prior context and the **name greeting** (Flow I) is presented, so the user is greeted by name. The carried-forward **summary** is **not** re-presented here — starting a new conversation is a deliberate fresh start. (The durable summary still persists and reappears on the next *open* onto an empty conversation, per Flow G.) |
| 4 | Authenticated User | Send the first new message | On the next send, the frontend generates a fresh `conversationId` (Flow B step 2), beginning a new session. |
| 5 | System (Agent) | Overwrite summary on first turn | When the new conversation's first turn completes, the user-scoped `ConversationSummary` is regenerated from the new conversation (Flow B step 11), overwriting the carried-forward summary. |

### Postconditions

- The previous conversation is unreachable; a new session begins on the next message.
- The popup is cleared of prior context and greets the user by name (Flow I); no summary greeting is shown at the moment of starting the new conversation.
- The durable summary carries forward (and reappears on the next *open* onto an empty conversation, per Flow G) until the new conversation's first turn overwrites it.

---

## Flow G — Resume with Summary Greeting

When the user opens the assistant and a durable summary exists, the agent opens with a visible summary of the previous conversation. This is how continuity is surfaced after a reload, a restart, or reopening the popup. When no summary exists, the agent instead greets the user by name (Flow I).

### Actors

- **Authenticated User**: A logged-in user opening the assistant.
- **Guest Browser**: The React frontend rendering the opening greeting.
- **System (Agent)**: The `agent` backend reading the user-scoped `ConversationSummary`.

### Preconditions

- The user has an active session (is logged in).
- A user-scoped `ConversationSummary` (`last_conversation`) exists for this user. (If none exists — e.g. the first conversation ever — the agent presents the name greeting instead; see Flow I.)

> **Open implementation detail (left to decomposition):** the transport for presenting the opening summary is not prescribed here. It may be a lightweight greeting trigger when the popup opens, or the summary may be prefixed to the first agent reply. The behaviour below is fixed; the mechanism is an implementation choice.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Open the assistant | Opens the popup (on first open after a reload/restart, or on reopening the popup onto an empty conversation). |
| 2 | Guest Browser | Request opening greeting | With an empty local transcript, requests the opening greeting from the backend. |
| 3 | System (Agent) | Read durable summary | Reads the user-scoped `ConversationSummary` (`last_conversation`) for the JWT-resolved `userId`. |
| 4 | System (Agent) | Return greeting | If a summary exists, returns a visible summary message describing the previous conversation. If no summary exists, the agent instead returns the name greeting (Flow I). |
| 5 | Guest Browser | Render greeting | Renders the summary (or the name greeting) as the opening assistant message. The user continues the conversation from there (Flow B). |

### Postconditions

- The agent has presented the previous conversation's summary as the opening message, or — when no summary exists — a name greeting (Flow I).
- The user can continue; the next message starts a fresh `conversationId` while the agent's context is seeded by the summary.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| No prior summary | First conversation ever for this user | No summary is shown. The agent presents the name greeting instead (Flow I). |
| Summary read fails | Reading `last_conversation` errors | The popup opens without a summary; the agent may still present the name greeting (Flow I). No error is surfaced. Normal chat proceeds. |
| Unauthenticated request | No valid JWT | System returns HTTP 401. Frontend redirects to `/login`. |

---

## Flow H — Regenerate Conversation Summary (per turn)

After each completed turn, the backend regenerates the durable user-scoped summary. This flow has no direct user interaction — it runs on the backend as part of completing a turn.

### Actors

- **System (Agent)**: The `agent` backend and the PostgreSQL-backed ADK `SessionService`.

### Preconditions

- A turn has just completed (Flow B step 10 / Flow C step 5).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Agent) | Build summary | Generates a condensed summary of the current conversation, including the just-completed turn. |
| 2 | System (Agent) | Persist to user scope | Writes the summary to user-scoped ADK state under `last_conversation` for the conversation's `userId`, refreshing `updatedAt`. Persisted via the PostgreSQL-backed ADK `SessionService`. |

### Postconditions

- The user-scoped `ConversationSummary` reflects the most recent turn and is durable across restart and reload.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Persist fails | The summary write to the session store errors | The reply is unaffected. The summary retains its previous value; the next successful turn refreshes it. No error is surfaced to the user. |

---

## Flow I — Resolve User Name and Greet by Name

The assistant greets the user by name and makes the name available to the Main Agent for the whole conversation. The name is resolved from a durable, user-scoped cache and, on a miss, fetched from the User domain and cached for future sessions. This flow has no screen of its own — it supplies the greeting presented on the blank new-conversation view (Flow F) and on open when no summary exists (Flow G).

### Actors

- **Authenticated User**: A logged-in user opening the assistant or starting a new conversation.
- **Guest Browser**: The React frontend rendering the opening greeting.
- **System (Agent)**: The `agent` backend reading/writing the user-scoped `AgentUserProfile`.
- **System (User)**: The User backend service, reached via its `api/` interface for a name lookup by `userId`.

### Preconditions

- The user has an active session (is logged in); the request carries a valid JWT.
- The assistant is opening onto an empty conversation and **no `ConversationSummary` is being presented** (first-ever user, or the blank new-conversation view with no summary).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Agent) | Resolve userId | Resolves `userId` from the JWT `sub` claim (`SecurityContextHolder`), never from the request body. |
| 2 | System (Agent) | Read cached profile | Reads the user-scoped `AgentUserProfile` state (`user_profile`). If present, uses its `displayName`. |
| 3 | System (User) | Fetch name on miss | On a cache miss, the agent calls the User domain's `api/` interface with `userId` to obtain `firstName`. |
| 4 | System (Agent) | Resolve and cache displayName | Resolves `displayName` from `firstName`; when `firstName` is missing or blank, uses a generic fallback (e.g. "there"). On a successful fetch, writes `displayName` to the user-scoped `user_profile` state for reuse by all future sessions. |
| 5 | System (Agent) | Provide name to Main Agent | Supplies `displayName` to the Main Agent's context/instruction so it can address the user by name throughout the conversation. |
| 6 | System (Agent) | Return name greeting | Returns a visible greeting that addresses the user by name (e.g. "Hello {displayName}"). |
| 7 | Guest Browser | Render greeting | Renders the name greeting as the opening assistant message. The user continues the conversation from there (Flow B). |

### Postconditions

- The user has been greeted by name on the empty-open / blank-new-conversation view when no summary was presented.
- `displayName` is available to the Main Agent for the remainder of the conversation.
- On a successful fetch, `AgentUserProfile` is populated and reused by future sessions.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Name fetch fails | The User `api/` call errors or times out | The agent greets with the generic fallback (e.g. "Hello there") and does **not** cache a value, so it retries the fetch on the next attempt. No error is surfaced to the user. |
| firstName missing or blank | The User record has no usable first name | The agent uses the generic fallback for `displayName`. |
| Stale cached name | The user changed their profile name after the cache was written | The previously cached `displayName` is used; staleness is accepted in this iteration (see `decisions/2026-10-17-agent-personalised-name-greeting.md`). |
| Unauthenticated request | No valid JWT | System returns HTTP 401. Frontend redirects to `/login`. |

---

## Query Request Specification

`POST /api/v1/agent/query`

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| accountId | uuid | yes | The account selected in frontend state. Carried on every request and consumed by the Portfolio Analyst's holdings tool to scope portfolio retrieval to this account. |
| conversationId | uuid | yes | Frontend-generated conversation key. New on the first turn and after a "New conversation" reset; reused on every subsequent turn of the same conversation. |
| message | string | yes | The user's message text. |

- `userId` is resolved server-side from the JWT and is never present in the request body. It is also the `userId` supplied to the holdings tool, the key for the user-scoped `ConversationSummary`, and the key for the user-scoped `AgentUserProfile`.
- The response is a Server-Sent Events stream of reply tokens (ADK `StreamingMode.SSE`). If streaming cannot be established, a single buffered reply is returned instead.

---

## Domain Models Involved

- **AgentConversation**: Created on the first query for a `conversationId` and appended to on every subsequent turn. Held in the ephemeral ADK session; its verbatim turns are not durably persisted. Its `accountId` scopes the Portfolio Analyst's holdings tool call. See `domain/model/agent-conversation.md`.
- **ConversationSummary**: Regenerated after each turn (Flow H) and presented as the opening summary when the assistant is opened onto an empty conversation (Flow G). The single durable continuity artefact, held as user-scoped ADK state (`last_conversation`). See `domain/model/conversation-summary.md`.
- **AgentUserProfile**: Durable, user-scoped ADK state (`user_profile`) caching the user's greeting `displayName`. Read on open; populated on a miss from the User domain's `api/` interface. Supplies the name greeting (Flow I) and the name injected into the Main Agent's context. See `domain/model/agent-user-profile.md`.
- **Position**: Read by the holdings tool via the Portfolio `api/` interface (the same read path as `GET /api/v1/portfolio/holdings`). Fields used: `ticker`, `quantity`, `avgPrice`, plus live `currentPrice` and computed unrealised P&L. Never shared as an entity across domains; the `agent` domain consumes only the tool result. See `domain/model/position.md`.
- **User**: The user's `firstName` is read via the User domain's `api/` interface (keyed by the JWT-resolved `userId`) to resolve the greeting name (Flow I). The `agent` domain stores only the resolved `displayName` in `AgentUserProfile`, never the `User` entity. See `domain/model/user.md`.
- **Session**: `userId` is resolved server-side from the JWT (see `domain/flows/jwt-authentication.md`) and is the identity passed to the holdings tool, used as the summary scope key, and used as the `AgentUserProfile` scope key. The frontend `accessToken` is attached via the shared Axios interceptor. The widget only renders while a frontend session exists.
- **Account**: The selected `accountId` is read from the `portfolio` Zustand slice and sent with each query. It scopes the holdings tool to a single account. The `agent` domain stores the UUID only; no `Account` entity is shared across domains.
