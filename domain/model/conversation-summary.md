# ConversationSummary

## Overview

Represents a durable, condensed summary of a single user's most recent AI Assistant conversation, owned by the `agent` domain. It is the one piece of conversation continuity that survives a backend restart, a frontend reload, and a user starting a new conversation. It is held in ADK **user-scoped** state under the key `last_conversation`, persisted through the PostgreSQL-backed ADK `SessionService` (see `decisions/2026-10-10-agent-conversation-summary-continuity.md`).

`ConversationSummary` is deliberately **not** the verbatim transcript. The turn-by-turn history of a conversation lives only in the ephemeral ADK session (`domain/model/agent-conversation.md`) and is discarded on restart. The summary is a lossy, regenerated digest that lets the agent greet a returning user with an account of what was discussed before, without replaying the full history. There is exactly one `ConversationSummary` per user — the platform does not track multiple past conversations.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| userId | uuid | yes | The authenticated user who owns the summary. Acts as the ADK user-scope key. Resolved server-side from the JWT — never from the request body. |
| key | string | yes | The ADK user-scoped state key under which the summary is stored. Always `last_conversation`. |
| summary | string | yes | A condensed natural-language summary of the user's most recent conversation. Regenerated after each completed turn. Not a verbatim transcript. |
| updatedAt | datetime | yes | Timestamp of the last regeneration of the summary. |

## Behaviors

- **Regenerate**: After each completed turn of the active conversation, the backend regenerates `summary` from the current conversation and writes it to the user-scoped `last_conversation` state, refreshing `updatedAt`. Because it is updated every turn, the summary is always current — even if the backend restarts mid-conversation before the user starts a new one.
- **Present on open**: When the user opens the assistant (on resume after a reload/restart, or on starting a new conversation), the agent reads `last_conversation` and presents the summary as a visible opening message. See `domain/flows/agent-assistant-chat.md` — Flow G.
- **CarryForward**: When the user clicks "New conversation", the previous session is abandoned but `last_conversation` persists unchanged. It continues to be presented until the new conversation produces its first summary regeneration, at which point it is overwritten.

## Events

_No domain events are emitted. `ConversationSummary` is user-scoped ADK state internal to the `agent` domain._

## Relationships

- **User** (`reference`, unique): Exactly one `ConversationSummary` exists per user, keyed by `userId` at ADK user scope. Reference by ID only — no foreign key, no shared entity across domains.
- **AgentConversation** (`reference`): The summary is derived from the active conversation's turns. It does not own or embed the conversation; the conversation is ephemeral while the summary is durable. See `domain/model/agent-conversation.md`.

## Business Rules

- There is at most one `ConversationSummary` per user — stored as user-scoped ADK state under the key `last_conversation`.
- The summary is durable: it is persisted via the PostgreSQL-backed ADK `SessionService` and survives backend restarts and frontend reloads.
- The summary is regenerated after every completed turn and always reflects the user's most recent conversation.
- The summary is a lossy digest, not the verbatim transcript. Full turn-by-turn conversation history is **not** durably stored.
- On a "New conversation" reset, the existing summary carries forward unchanged until the new conversation's first turn regenerates it.
- The first conversation a user ever has no prior summary; the agent opens that conversation without a summary greeting.
- `userId` is always resolved from the JWT and never taken from the request body.
- Only the `agent` domain reads or writes `ConversationSummary`. It is not exposed to other domains.
