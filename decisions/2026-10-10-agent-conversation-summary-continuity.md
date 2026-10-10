# Decision: Summary-based durable continuity for the AI assistant

**Date:** 2026-10-10  
**Status:** accepted

## Context

Issue #207 requests replacing ADK Java's `InMemorySessionService` with a PostgreSQL-backed persistent `SessionService` so that agent sessions and full conversation history survive restarts and are shared across application instances. The issue's persistence model is full-fidelity: persist every `Session` and every `Event` (turn-by-turn history) as the durable source of truth.

The existing domain docs state the opposite of durability: `domain/model/agent-conversation.md`, `domain/flows/agent-assistant-chat.md`, and `domain/usecases/chat-with-ai-assistant.md` all describe conversations as in-memory only and lost on restart.

During product refinement (sparring on issue #207) we chose a narrower, deliberately different behaviour from the issue's full-history model:

- One active conversation per user. No history browser, no multi-conversation tracking.
- Continuity is provided by a durable, **user-scoped summary** (ADK state key `last_conversation`), not by replaying full verbatim event history.
- The summary is regenerated after **each turn**, so it is always current — it survives a mid-conversation restart, not just an end-of-conversation one.
- On return (reload, restart, or after clicking "New conversation"), the agent opens with a **visible summary** of the previous conversation.
- A "New conversation" button starts a fresh session; the previous conversation becomes unreachable; the summary carries forward until the new conversation's first turn overwrites it.
- The verbatim turn-by-turn session remains ephemeral.

## Decision

- Adopt a persistent (PostgreSQL-backed) ADK `SessionService` to persist ADK **user-scoped state**, replacing `InMemorySessionService` through configuration/wiring, reusing the application's existing PostgreSQL `DataSource` (per issue #207).
- Treat the user-scoped `ConversationSummary` (`last_conversation`) as the **single durable continuity artefact**. Do **not** rely on durable verbatim event-history replay for the user-facing continuity behaviour.
- Regenerate the summary after each completed turn and persist it as user-scoped state.
- Present the summary as the agent's opening message on resume and on new-conversation start.
- Keep the verbatim ADK session ephemeral.

This deliberately contradicts:

1. **Issue #207's stated persistence model** — the issue makes full `Session` + `Event` history the durable source of truth. We instead make the user-scoped summary the durable artefact and do not treat full verbatim history as a product requirement.
2. **The prior domain statements** that conversations are in-memory only and lost on restart (`agent-conversation` model, `agent-assistant-chat` flow, `chat-with-ai-assistant` use case). Continuity now survives via the summary.

## Consequences

- New domain entity: `domain/model/conversation-summary.md` (`ConversationSummary`).
- Updated domain docs: `domain/model/agent-conversation.md` (session is ephemeral; summary is the durable continuity artefact; new-conversation reset semantics); `domain/flows/agent-assistant-chat.md` (revised Flows B, C, D; new Flow F — Start a New Conversation, Flow G — Resume with Summary Greeting, Flow H — Regenerate Conversation Summary); `domain/usecases/chat-with-ai-assistant.md` (happy path, failure scenarios, flows list, out-of-scope).
- A persistent ADK `SessionService` backed by the existing PostgreSQL `DataSource` is required, with migrations, concurrency-safe writes, and wiring to replace `InMemorySessionService`. Its implementation need only satisfy the ADK `BaseSessionService` contract plus durable user-scoped state — full verbatim history persistence is not a product requirement and should be scoped to what the ADK contract demands.
- The transport for presenting the opening summary (a lightweight greeting trigger vs. prefixing the first agent reply) is intentionally left to decomposition/implementation. The behaviour is fixed; the mechanism is not.
- Out of scope remains: multiple concurrent conversations, conversation history browsing, and durable verbatim transcript.
- Decomposition note: issue #207's broader deliverables (full event-history tables, list/delete-session surface area, etc.) should be reconciled against this decision before task breakdown — build the SessionService to the ADK contract, but drive the product behaviour from the user-scoped summary.
