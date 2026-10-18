# Decision: Personalised name greeting in the AI Assistant

**Date:** 2026-10-17  
**Status:** accepted

## Context

Issue #208 asks that when a user opens a new chat session with the AI Assistant,
the agent greets them by their name (e.g. "Hello {name}"). The Main Agent should
have the user's name available in session state. If it is not present, the agent
should fetch the name from the User domain (keyed by the JWT-resolved `userId`)
and persist it to ADK **user-scoped** state so it is available to all future
sessions.

This intersects two existing, deliberately-designed behaviours in
`domain/flows/agent-assistant-chat.md`:

- **Flow G** opens the assistant with the user's `ConversationSummary`
  (`last_conversation`) when one exists; when none exists the popup opens empty
  with **no greeting**.
- **Flow F** plus `decisions/2026-10-17-new-conversation-blank-slate.md` (issue
  #213) made the **"New conversation"** button open on a **blank history with no
  greeting**, tying all greetings to the moment the assistant is *opened*.

Product decisions taken while sparring issue #208:

- The name greeting appears whenever the assistant opens onto an empty
  conversation and there is **no summary** to present — i.e. it replaces the
  current "opens empty, no greeting" case. This covers a first-ever user.
- It also appears on the blank **"New conversation"** view when no summary
  exists. This partially revises `decisions/2026-10-17-new-conversation-blank-slate.md`:
  that view is no longer unconditionally silent — a name greeting is shown there
  when (and only when) there is no summary to carry forward.
- The name is the user's `firstName`, with a generic fallback (e.g. "there")
  when `firstName` is absent or blank.
- The name is loaded into the Main Agent's context so it can address the user by
  name throughout the conversation, not only in the opening greeting.
- The name is cached in user-scoped ADK state and **staleness is accepted**: a
  later profile name change is not reflected until the cache is (manually or in a
  future iteration) invalidated.

## Decision

- Introduce a new `agent`-domain entity, `AgentUserProfile`
  (`domain/model/agent-user-profile.md`): durable, user-scoped ADK state under
  key `user_profile` caching the resolved `displayName`. One per user.
- The agent resolves the greeting name by reading `user_profile`; on a miss it
  fetches `firstName` from the **User domain's `api/` interface** by
  `userId`, resolves `displayName`, and caches it. This introduces a new
  synchronous cross-domain read path (User → Agent) following the existing
  `api/` pattern in `standards/architecture.md`. A failed fetch is not cached;
  the agent falls back to the generic greeting and retries next time.
- Add **Flow I — Resolve User Name and Greet by Name** to
  `domain/flows/agent-assistant-chat.md`. Amend **Flow G** (no-summary branch
  now shows the name greeting) and **Flow F** (blank new-conversation view shows
  the name greeting when no summary exists). The Main Agent instruction is
  extended to reference the user's name.
- The cached name is **not** invalidated on profile name change in this
  iteration; staleness is accepted.

## Consequences

- Updated domain docs:
  - `domain/model/agent-user-profile.md` — new entity (created).
  - `domain/flows/agent-assistant-chat.md` — new Flow I; Flow F and Flow G
    amended; Main Agent hierarchy note added; Domain Models Involved extended.
  - `domain/usecases/chat-with-ai-assistant.md` — new domain model and flow
    references, happy-path and failure-scenario updates.
- This partially revises `decisions/2026-10-17-new-conversation-blank-slate.md`:
  the "New conversation" view is no longer unconditionally silent — it shows a
  name greeting when there is no summary. The summary-carry-forward and
  open-time summary greeting (Flow G) are otherwise unchanged.
- A new synchronous User → Agent `api/` read path must be implemented when the
  backend is built. The User domain must expose a name lookup by `userId`
  through its `api/` package; the `agent` domain consumes only that interface.
- Cached greeting names can be stale after a profile name change until a future
  invalidation mechanism is added. Revisit if/when a profile-name-edit feature
  ships.
