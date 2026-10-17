# Decision: "New conversation" opens on a blank history, not the carried-forward summary

**Date:** 2026-10-17  
**Status:** accepted

## Context

Issue #213 reports that clicking "New conversation" in the AI assistant popup opens a
new conversation but still displays the summary of the previous one. The user's
expectation is that starting a new conversation is a deliberate fresh start and should
wipe the visible state.

The prior behaviour was intentional per `decisions/2026-10-10-agent-conversation-summary-continuity.md`
and `domain/flows/agent-assistant-chat.md` (Flow F): on "New conversation" the popup
re-presented the carried-forward summary as an opening greeting. The implementation also
had a stale-cache race — it re-seeded the *previously cached* summary rather than a freshly
fetched one — so what the user saw was often stale.

Product clarification on issue #213: the two triggers are distinct.

- **"New conversation" button** → start from scratch, blank history, no greeting.
- **Opening the assistant fresh** (page reload, backend restart, or reopening the popup
  onto an empty conversation) → still show the previous conversation's summary as the
  opening greeting (Flow G is unchanged).

## Decision

- Clicking "New conversation" wipes the in-memory transcript and `conversationId` and
  opens on a **blank history**. The carried-forward summary is **not** re-presented at
  that moment.
- The durable, user-scoped `ConversationSummary` (`last_conversation`) is **unchanged** on
  the backend: it still carries forward until the new conversation's first turn overwrites
  it, and it is still surfaced as the opening greeting the next time the assistant is
  **opened** onto an empty conversation (Flow G).
- Frontend: the summary-greeting seed is suppressed immediately after an explicit
  "New conversation" and re-enabled when the popup is next opened. The unconditional
  `refetch` on "New conversation" (the source of the stale greeting) is removed.

This deliberately revises — but does not fully reverse — `decisions/2026-10-10-agent-conversation-summary-continuity.md`,
which listed "after clicking New conversation" as a moment the agent "opens with a visible
summary". That greeting now happens only on **open**, never at the instant of starting a
new conversation. The durable-summary continuity model itself is retained.

## Consequences

- Updated domain docs:
  - `domain/flows/agent-assistant-chat.md` — Flow F (steps, preconditions, postconditions)
    now opens on a blank history; Flow G no longer lists "New conversation" as a greeting
    trigger; the ConversationSummary note is reworded to "opened onto an empty conversation".
  - `domain/model/conversation-summary.md` — Present-on-open and CarryForward behaviours
    clarified.
  - `domain/usecases/chat-with-ai-assistant.md` — happy-path step 10 reworded.
- Frontend changes (`services/front-end`):
  - `useAgentStore` gains a `suppressSummaryGreeting` flag, set by `startNewConversation`
    and cleared by `open`/`toggle`-to-open and `reset`.
  - `AssistantChatPopup` gates the greeting effect on `!suppressSummaryGreeting` and drops
    the `refetch` call from the "New conversation" handler.
- No backend change: summary persistence and carry-forward semantics are untouched.
