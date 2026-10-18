# AgentUserProfile

## Overview

Represents a durable, user-scoped cache of a single user's greeting name, owned by the `agent` domain. It exists so the Trade Lab AI Assistant can address the user by name — both in its opening greeting and throughout a conversation — without re-querying the User domain on every turn. It is held in ADK **user-scoped** state under the key `user_profile`, persisted through the PostgreSQL-backed ADK `SessionService` (the same mechanism as `ConversationSummary`; see `decisions/2026-10-10-agent-conversation-summary-continuity.md`).

`AgentUserProfile` is a lazy, lossy cache — not the authoritative record. The user's real identity lives in the User domain (`domain/model/user.md`). The agent fetches the user's `firstName` from the User domain's `api/` interface the first time it needs it, resolves a display name, and caches it here for reuse by all future sessions. The cache is deliberately **not** invalidated when the user later changes their profile name, so the greeting name may go stale (see `decisions/2026-10-17-agent-personalised-name-greeting.md`). There is exactly one `AgentUserProfile` per user.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| userId | uuid | yes | The authenticated user who owns the cached profile. Acts as the ADK user-scope key. Resolved server-side from the JWT `sub` claim — never from the request body. |
| key | string | yes | The ADK user-scoped state key under which the profile is stored. Always `user_profile`. |
| displayName | string | yes | The resolved name the agent uses to address the user. Derived from the User domain's `firstName`. When `firstName` is missing or blank, a generic fallback (e.g. "there") is used instead. |
| updatedAt | datetime | yes | Timestamp of the last time the cached profile was written. |

## Behaviors

- **Resolve**: When the agent needs the user's name (opening greeting, or addressing the user mid-conversation), it reads the user-scoped `user_profile` state. If present, `displayName` is used as-is.
- **FetchAndCache**: On a cache miss, the agent calls the User domain's `api/` interface with the JWT-resolved `userId` to obtain `firstName`, resolves `displayName` (`firstName`, or a generic fallback when absent/blank), writes it to user-scoped `user_profile` state, and refreshes `updatedAt`. The resolved name is then reused by all future sessions for this user.
- **ProvideToAgent**: `displayName` is supplied to the Main Agent's context/instruction so the agent can greet and address the user by name at any point in the conversation, not only in the opening greeting. See `domain/flows/agent-assistant-chat.md` — Flow I.

## Events

_No domain events are emitted. `AgentUserProfile` is user-scoped ADK state internal to the `agent` domain._

## Relationships

- **User** (`reference`, unique): Exactly one `AgentUserProfile` exists per user, keyed by `userId` at ADK user scope. `displayName` is derived from the User domain's `firstName`, read via the User domain's `api/` interface. Reference by ID only — no foreign key, and the `agent` domain never holds the `User` entity.
- **ConversationSummary** (`sibling`): A separate piece of durable, user-scoped ADK state owned by the `agent` domain (key `last_conversation`). The two are independent: the summary carries conversation continuity, this profile carries the greeting name. See `domain/model/conversation-summary.md`.

## Business Rules

- There is at most one `AgentUserProfile` per user — stored as user-scoped ADK state under the key `user_profile`.
- `userId` is always resolved from the JWT `sub` claim and never taken from the request body.
- `displayName` is derived from the User domain's `firstName`. When `firstName` is missing or blank, a generic fallback is used so a greeting can always be produced.
- `firstName` is fetched from the User domain exclusively via its `api/` interface. The `agent` domain stores only the resolved `displayName`, never the `User` entity.
- The cache is durable and is **not** invalidated when the user later changes their profile name. A stale greeting name is accepted in this iteration (see `decisions/2026-10-17-agent-personalised-name-greeting.md`).
- On a failed fetch from the User domain, no value is cached; the agent greets using the generic fallback and retries the fetch on the next attempt.
- Only the `agent` domain reads or writes `AgentUserProfile`. It is not exposed to other domains.
