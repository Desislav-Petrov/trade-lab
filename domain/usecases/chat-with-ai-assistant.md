# Use Case: Chat with the AI Assistant

## Goal

An authenticated user opens the Trade Lab AI Assistant from anywhere on the platform and holds a streamed, multi-turn conversation with the platform's agent, which routes portfolio questions to a specialised Portfolio Analyst sub-agent that grounds its answers in the user's real holdings for the selected account.

## Actor

Authenticated User — a logged-in user on any page. The widget is not shown to logged-out users.

## Screen

- **Widget:** "Trade Lab AI Assistant", docked at the bottom of every page, available platform-wide to logged-in users.
- **Popup:** Chat popup opened from the widget.
- **Entry point:** User clicks the docked assistant widget on any page.

## Trigger

User clicks the assistant widget and sends a message.

## Domain Models

- `domain/model/agent-conversation`
- `domain/model/position`
- `domain/model/session`
- `domain/model/account`

## Flows

- `domain/flows/agent-assistant-chat` (Flow A — Open and Close the Assistant Widget)
- `domain/flows/agent-assistant-chat` (Flow B — Send a Message and Stream the Reply)
- `domain/flows/agent-assistant-chat` (Flow C — Continue a Multi-Turn Conversation)
- `domain/flows/agent-assistant-chat` (Flow D — Transcript Persistence and Loss)
- `domain/flows/agent-assistant-chat` (Flow E — Portfolio Analyst Holdings Tool Call)

## Happy Path

1. A logged-in user sees the "Trade Lab AI Assistant" widget docked at the bottom of the page and clicks it to open the chat popup.
2. The user types a portfolio question and sends it. The frontend generates a `conversationId`, renders the user's message, and calls `POST /api/v1/agent/query` with `accountId`, `conversationId`, and `message`. The JWT is attached automatically.
3. The backend resolves `userId` from the JWT and `AgentService` forwards the message to the ADK Main Agent for the session.
4. The Main Agent recognises a portfolio question and delegates it to the Portfolio Analyst sub-agent — it never analyses the portfolio itself.
5. The Portfolio Analyst calls its holdings tool with the conversation's `accountId` and the JWT-resolved `userId`, retrieves the user's real priced holdings and unrealised P&L via the Portfolio `api/` interface, and grounds its answer in that data.
6. The reply streams back token-by-token via Server-Sent Events and renders live in the popup.
7. The user sends a follow-up message. The frontend reuses the same `conversationId`; the ADK session supplies prior context so the answer is in-context, calling the holdings tool again when fresh data is needed.
8. The user closes the popup and navigates elsewhere; the transcript persists in frontend session state and the widget remains available on every page.
9. The user reloads the page; the transcript is cleared and the next message begins a new conversation.

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
| Backend restarted between turns | The old `conversationId` is treated as a new conversation with empty context; earlier turns are not reflected. |

## Out of Scope

- Trade-fill / price history in the assistant — the Portfolio Analyst's only tool is holdings; it cannot read the fill history (`GET /api/v1/portfolio/fills`) or Advanced Insights data.
- Durable conversation history — conversations are in-memory only, on both the frontend (lost on reload) and the backend (lost on restart).
- Any sub-agent other than the Portfolio Analyst.
- Voice, audio, image, or file input (no ADK BIDI/live streaming).
- Cross-account or multi-account reasoning within a single conversation — a single `accountId` is carried per conversation and the holdings tool is scoped to it.
- Actions that mutate state (placing trades, changing settings) initiated from the assistant.
- Authentication or account selection UI inside the popup — the widget relies on the existing session and the selected account in frontend state.
