# Use Case: Chat with the AI Assistant

## Goal

An authenticated user opens the Trade Lab AI Assistant from anywhere on the platform and holds a streamed, multi-turn conversation with the platform's agent, which routes portfolio questions to a specialised Portfolio Analyst sub-agent that grounds its answers in the user's real holdings for the selected account. The agent greets the user by name, and continuity across reloads and restarts is preserved by a durable summary of the user's previous conversation.

## Actor

Authenticated User — a logged-in user on any page. The widget is not shown to logged-out users.

## Screen

- **Widget:** "Trade Lab AI Assistant", docked at the bottom of every page, available platform-wide to logged-in users.
- **Popup:** Chat popup opened from the widget, including a "New conversation" button.
- **Entry point:** User clicks the docked assistant widget on any page.

## Trigger

User clicks the assistant widget and sends a message.

## Domain Models

- `domain/model/agent-conversation`
- `domain/model/conversation-summary`
- `domain/model/agent-user-profile`
- `domain/model/position`
- `domain/model/session`
- `domain/model/account`

## Flows

- `domain/flows/agent-assistant-chat` (Flow A — Open and Close the Assistant Widget)
- `domain/flows/agent-assistant-chat` (Flow B — Send a Message and Stream the Reply)
- `domain/flows/agent-assistant-chat` (Flow C — Continue a Multi-Turn Conversation)
- `domain/flows/agent-assistant-chat` (Flow D — Transcript Persistence and Loss)
- `domain/flows/agent-assistant-chat` (Flow E — Portfolio Analyst Holdings Tool Call)
- `domain/flows/agent-assistant-chat` (Flow F — Start a New Conversation)
- `domain/flows/agent-assistant-chat` (Flow G — Resume with Summary Greeting)
- `domain/flows/agent-assistant-chat` (Flow H — Regenerate Conversation Summary)
- `domain/flows/agent-assistant-chat` (Flow I — Resolve User Name and Greet by Name)

## Happy Path

1. A logged-in user sees the "Trade Lab AI Assistant" widget docked at the bottom of the page and clicks it to open the chat popup. On a first-ever open with no prior summary, the agent greets the user by their first name (Flow I).
2. The user types a portfolio question and sends it. The frontend generates a `conversationId`, renders the user's message, and calls `POST /api/v1/agent/query` with `accountId`, `conversationId`, and `message`. The JWT is attached automatically.
3. The backend resolves `userId` from the JWT and `AgentService` forwards the message to the ADK Main Agent for the session. The Main Agent has the user's name available so it can address them by name.
4. The Main Agent recognises a portfolio question and delegates it to the Portfolio Analyst sub-agent — it never analyses the portfolio itself.
5. The Portfolio Analyst calls its holdings tool with the conversation's `accountId` and the JWT-resolved `userId`, retrieves the user's real priced holdings and unrealised P&L via the Portfolio `api/` interface, and grounds its answer in that data.
6. The reply streams back token-by-token via Server-Sent Events and renders live in the popup. After it completes, the backend regenerates the durable user-scoped summary (`last_conversation`).
7. The user sends a follow-up message. The frontend reuses the same `conversationId`; the ADK session supplies prior context so the answer is in-context, calling the holdings tool again when fresh data is needed.
8. The user closes the popup and navigates elsewhere; the transcript persists in frontend session state and the widget remains available on every page.
9. The user reloads the page (or returns the next day, possibly after a backend restart); the verbatim transcript is gone, but on reopening the agent opens with a summary of the previous conversation drawn from the durable `last_conversation` state, and the user continues from there.
10. The user clicks "New conversation" to start a fresh session; the previous conversation becomes unreachable and the popup opens on a blank history (no summary greeting at that moment; a name greeting is shown only when no summary exists). The durable summary still carries forward and reappears the next time the user opens the assistant, until the new conversation's first turn overwrites it.

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| User is logged out | The widget is not rendered. |
| No account selected in frontend state | Send is blocked; the user is prompted to select an account first. No request is made. |
| Unauthenticated request (HTTP 401) | Frontend redirects to `/login`. |
| Holdings tool fails (account not owned/found, or price/balance data unavailable) | The Portfolio Analyst surfaces the error to the user rather than answering from general knowledge. |
| SSE stream drops mid-reply | Popup shows the partial reply and an inline error inviting a retry. |
| Agent/backend error before streaming | Popup shows "The assistant is unavailable. Please try again."; the user's message is retained for resend. |
| Streaming cannot be established | Fallback to a single buffered reply rendered at once. |
| Backend restarted between turns | The verbatim session is gone; the agent recovers continuity from the durable user-scoped summary and opens with it. Earlier turns are reflected in summary form, not verbatim. |
| Summary regeneration or read fails | The reply is unaffected; the summary retains its previous value and the next turn refreshes it. No error is surfaced; continuity falls back to the prior summary. |
| No prior summary (first conversation ever) | The popup opens with a name greeting (Flow I) instead of an empty conversation. |
| Name fetch fails or firstName missing | The agent greets with a generic fallback (e.g. "Hello there"); no name is cached and it retries on the next attempt (Flow I). |

## Out of Scope

- Trade-fill / price history in the assistant — the Portfolio Analyst's only tool is holdings; it cannot read the fill history (`GET /api/v1/portfolio/fills`) or Advanced Insights data.
- Durable **verbatim** conversation history — only a condensed user-scoped summary persists; the turn-by-turn transcript is lost on frontend reload and backend restart.
- Tracking, listing, or browsing multiple conversations — there is exactly one active conversation per user, and previous conversations are unreachable once a new one starts.
- Any sub-agent other than the Portfolio Analyst.
- Voice, audio, image, or file input (no ADK BIDI/live streaming).
- Cross-account or multi-account reasoning within a single conversation — a single `accountId` is carried per conversation and the holdings tool is scoped to it.
- Actions that mutate state (placing trades, changing settings) initiated from the assistant.
- Authentication or account selection UI inside the popup — the widget relies on the existing session and the selected account in frontend state.
- Invalidating or refreshing the cached greeting name when the user changes their profile name — staleness is accepted in this iteration (see `decisions/2026-10-17-agent-personalised-name-greeting.md`).
