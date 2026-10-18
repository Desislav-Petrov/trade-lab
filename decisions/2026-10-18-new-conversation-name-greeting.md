# Decision: "New conversation" clears prior context and greets the user by name

**Date:** 2026-10-18
**Status:** accepted

**Supersedes:** `decisions/2026-10-17-new-conversation-blank-slate.md`

## Context

Owner feedback: clicking "New conversation" in the Trade Lab AI Assistant should clear
any previous context and present a name greeting, so the user is always acknowledged by
name when they start over. The prior decision (`2026-10-17-new-conversation-blank-slate.md`,
from issue #213) made "New conversation" open on a fully blank history with no greeting
unless the user had no durable summary. That left users with a carried-forward summary
seeing nothing at all when starting a new conversation.

## Decision

- Clicking "New conversation" wipes the in-memory transcript and `conversationId` (prior
  context is cleared and the previous conversation becomes unreachable, as before) and now
  always presents the **name greeting** (Flow I, `Hello {firstName}`). The carried-forward
  **summary** is still **not** re-presented at that moment.
- The durable, user-scoped `ConversationSummary` (`last_conversation`) is **unchanged** on
  the backend. It still carries forward and is surfaced as the opening summary the next
  time the assistant is opened onto an **empty** transcript (e.g. after a page reload or
  backend restart), per Flow G, until the new conversation's first turn overwrites it.
- Frontend: the greeting effect, when the summary is suppressed immediately after
  "New conversation", now falls through to the name greeting instead of rendering nothing.

This revises `decisions/2026-10-17-new-conversation-blank-slate.md`: the "no greeting at
the moment of New conversation" rule is replaced by "name greeting at the moment of New
conversation". The durable-summary continuity model (Flow G, carry-forward, overwrite on
first turn) is retained unchanged.

## Consequences

- Updated domain docs:
  - `domain/flows/agent-assistant-chat.md` — Flow F (intro, step 3, postconditions).
  - `domain/usecases/chat-with-ai-assistant.md` — happy-path step 10.
  - `domain/model/conversation-summary.md` — Present-on-open / CarryForward wording.
- Frontend change (`services/front-end`):
  - `AssistantChatPopup` greeting effect falls through to the name greeting when the
    summary is suppressed; `AssistantChatPopup.test.tsx` updated accordingly.
  - No change to `useAgentStore` (the `suppressSummaryGreeting` flag still suppresses the
    summary; it no longer implies "no greeting").
- No backend change: summary persistence and carry-forward semantics are untouched.
- Name resolution for the greeting: `useConversationSummary` uses the `name` from `GET /api/v1/agent/summary` when present, and otherwise falls back to the signed-in user's `firstName` from the session store. This is required because the backend endpoint only populates `name` when no summary exists (`name = if (summary == null) resolveName(userId) else null` in `AgentApiDelegateImpl`), so after "New conversation" — where a summary typically still exists — the session is the reliable source for the name greeting. When neither is available, no greeting is seeded; the Flow I generic "Hello there" fallback remains out of scope for the client-seeded greeting in this iteration.
- Follow-up for the backend team (out of frontend scope): consider having `GET /api/v1/agent/summary` always resolve and return `name` alongside the summary, which would remove the need for the frontend session fallback.
