# Tasks — Chat with the AI Assistant: Personalised Name Greeting (issue #208)

> **Scope:** This is the Flow I extension of the `chat-with-ai-assistant` use case only
> (issue #208). The rest of the use case is already implemented. See
> `domain/flows/agent-assistant-chat.md` (Flow I, plus amended Flows F/G/B),
> `domain/model/agent-user-profile.md`, and
> `decisions/2026-10-17-agent-personalised-name-greeting.md`.

## Summary

When the assistant opens onto an empty conversation with **no summary** to present
(first-ever user, or the blank "New conversation" view with no summary), greet the
user by their first name. The name is also made available to the Main Agent so it can
address the user by name throughout the session.

- The User domain exposes the user's **full name** via its cross-domain `api/`
  interface (`UserLookupApi.getName`). The **frontend** splits on whitespace and uses
  the first token for the greeting ("Hello {firstToken}").
- The agent caches the resolved name in **ADK user-scoped state** (key
  `user:user_profile`, the `user:` = `State.USER_PREFIX`) via the existing
  `PersistentSessionService.updateUserScopedValue` / `getUserScopedValue` — the same
  mechanism as `ConversationSummary` (`user:last_conversation`). Shared across all of
  the user's sessions. Staleness after a later profile name change is accepted.

## Decomposition decisions / notes

1. **Greeting transport:** reuse the existing `GET /agent/summary` endpoint; add a
   nullable `name` field to `AgentSummaryResponse`. Backend returns `summary` when one
   exists, otherwise `name`. The greeting string is built on the **frontend**.
2. **No DB/REPO work.** `AgentUserProfile` is ADK user-scoped state, not a JPA table.
   It rides on the existing `AgentUserState` row + `PersistentSessionService`. No new
   entity/repository, and no change to `PersistentSessionService` itself.
3. **Two layers used that are mandated by the standards but not in the standard layer
   table:** `API` (cross-domain Kotlin interface in `{domain}.api`) for the User→Agent
   name lookup, and `CONFIG` (`config/AgentConfiguration` — agent runtime wiring) for the
   Main Agent instruction.
4. **Exact ADK mechanism** for surfacing the name inside the Main Agent instruction
   (state-key placeholder vs. seeded session state) is left to the backend engineer,
   grounded in issue #208's `{...real_name...}` instruction approach. The acceptance
   criteria fix the behaviour, not the ADK API call.

---

## Backend

### [USER-API-1] — Expose getName on UserLookupApi

**Layer:** API (cross-domain Kotlin interface — user.api)
**Domain:** user
**Use case:** chat-with-ai-assistant (Flow I / #208)
**Implements:** agent-assistant-chat Flow I — step 3 (Fetch name on miss); architecture "Cross-Domain Sync"
**Inputs:** userId: UUID
**Outputs:** New method on existing interface `UserLookupApi`: `fun getName(userId: UUID): String?`
**Acceptance criteria:**
- [ ] `getName(userId: UUID): String?` added to `org.dpp.tradelab.user.api.UserLookupApi` (alongside `existsById`).
- [ ] Returns the user's full name as a single string; `null` when no such user. No `User` entity or JPA type crosses the boundary.
- [ ] KDoc marks it as the published cross-domain contract for resolving a user's name.
**Depends on:** none

### [USER-SVC-1] — Implement getName in UserService

**Layer:** Service (user.service)
**Domain:** user
**Use case:** chat-with-ai-assistant (Flow I / #208)
**Implements:** agent-assistant-chat Flow I — step 3
**Inputs:** userId: UUID
**Outputs:** `UserService.getName(userId)` returning the full name or null
**Acceptance criteria:**
- [ ] `UserService` (already `: UserLookupApi`) implements `getName`, reading via `UserRepository`.
- [ ] Returns the full name (e.g. `"$firstName $lastName"`, trimmed; `firstName` alone when `lastName` is blank).
- [ ] Returns `null` when no user exists for `userId` (no exception thrown).
- [ ] Method is `@Transactional(readOnly = true)`.
- [ ] Unit tests (KoTest + mockito-kotlin): found → full name; found with blank lastName → firstName only; not found → null.
**Depends on:** USER-API-1

### [AGENT-SVC-1] — AgentUserProfileService: resolve & cache name (user scope)

**Layer:** Service (agent.service)
**Domain:** agent
**Use case:** chat-with-ai-assistant (Flow I / #208)
**Implements:** agent-assistant-chat Flow I — steps 1,2,4 & Error Cases (name fetch fails / name blank / stale cached name); AgentUserProfile behaviours Resolve, FetchAndCache
**Inputs:** userId: UUID
**Outputs:** New `AgentUserProfileService` with `resolveName(userId): String?`
**Acceptance criteria:**
- [ ] Reads cache via `persistentSessionService.getUserScopedValue(AGENT_APP_NAME, userId, "user:user_profile")`; if a non-blank String, returns it without calling `UserLookupApi` (cache hit / stale accepted).
- [ ] On miss, calls `userLookupApi.getName(userId)`; on a non-blank result, persists it via `persistentSessionService.updateUserScopedValue(AGENT_APP_NAME, userId, "user:user_profile", name)` and returns it.
- [ ] On null/blank name, or any exception from `UserLookupApi` / state read / state write, returns `null`, does NOT cache, and does NOT propagate the exception.
- [ ] No greeting string is built here (the UI formats it).
- [ ] Unit tests cover: cache hit, miss→fetch→cache, miss→null→no-cache, miss→fetch-throws→null-no-cache.
**Depends on:** USER-API-1 (compile); USER-SVC-1 (runtime)

### [AGENT-SVC-2] — Make the resolved name available to the Main Agent per query

**Layer:** Service (agent.service)
**Domain:** agent
**Use case:** chat-with-ai-assistant (Flow I / #208)
**Implements:** agent-assistant-chat Flow B — step 6; Flow I — step 5; Agent Hierarchy "Main Agent" note
**Inputs:** userId, accountId, conversationId (existing `AgentService.query` params)
**Outputs:** `AgentService` resolves the name and ensures it is present in the agent run state before `agentRunner.runAsync`
**Acceptance criteria:**
- [ ] During `query` / `loadOrCreateSession`, `AgentUserProfileService.resolveName(userId)` is invoked so `user:user_profile` user-scoped state is populated before the Main Agent runs (state is merged into session state by `PersistentSessionService.buildSession`).
- [ ] Existing streaming, timeout, error-mapping, and post-completion summary regeneration behaviour is unchanged.
- [ ] A name-resolution failure never breaks the query (fallback behaviour; reply still streams).
- [ ] Unit test asserts `resolveName` is called within `query` and that a resolution failure does not abort the stream.
**Depends on:** AGENT-SVC-1

### [AGENT-CONFIG-1] — Main Agent instruction addresses the user by name

**Layer:** Config (config.AgentConfiguration — agent runtime wiring; not in the standard layer table)
**Domain:** agent
**Use case:** chat-with-ai-assistant (Flow I / #208)
**Implements:** Agent Hierarchy "Main Agent" note; agent-assistant-chat Flow B — step 6; Flow I — step 5
**Inputs:** resolved name present in agent run state (`user:user_profile`) from AGENT-SVC-2
**Outputs:** Updated `mainAgent` instruction in `AgentConfiguration`
**Acceptance criteria:**
- [ ] The Main Agent instruction directs it to address the user by their **first name** (derived from the provided user-scoped name state) throughout the conversation.
- [ ] When no name is available, it addresses the user neutrally (no fabricated name).
- [ ] Delegation rules and all other Main Agent / Portfolio Analyst behaviour are unchanged.
**Depends on:** AGENT-SVC-2

### [AGENT-CONTROLLER-1] — Return name from GET /agent/summary when no summary

**Layer:** Controller (agent.controller)
**Domain:** agent
**Use case:** chat-with-ai-assistant (Flow I / #208)
**Implements:** agent-assistant-chat Flow G — steps 3–4; Flow I — step 4; use case failure "No prior summary"
**Inputs:** generated `AgentSummaryResponse(summary, name)`; userId from `SecurityContextHolder`
**Outputs:** Updated `AgentApiDelegateImpl.getConversationSummary()`
**Acceptance criteria:**
- [ ] When `conversationSummaryService.getSummary(userId)` is non-null → response `summary` set, `name` null.
- [ ] When it is null → `name = agentUserProfileService.resolveName(userId)` (which may itself be null), `summary` null.
- [ ] Missing/invalid principal still yields 401 (`InvalidTokenException`), unchanged. No greeting string is built server-side.
- [ ] `@SpringBootTest` + MockMvc tests: summary-present branch, no-summary→name branch, name-null branch, 401 branch.
**Depends on:** AGENT-API-CONTRACT-1, AGENT-SVC-1

### [AGENT-API-CONTRACT-1] — Add name field to AgentSummaryResponse

**Layer:** OpenAPI Contract (services/contract/agent-openapi.yaml) — last API-layer task
**Domain:** agent
**Use case:** chat-with-ai-assistant (Flow I / #208)
**Implements:** agent-assistant-chat Flow G / Flow I opening-greeting transport (decomposition decision #1)
**Inputs:** Operation `GET /agent/summary` (operationId `getConversationSummary`); schema `AgentSummaryResponse`
**Outputs:** Updated `services/contract/agent-openapi.yaml`
**Acceptance criteria:**
- [ ] `AgentSummaryResponse` gains `name: { type: string, nullable: true }`; `summary` remains nullable; neither is newly required.
- [ ] Operation description documents: `summary` when a prior summary exists, otherwise `name` carries the user's full name for the UI to greet with (both may be null for a first-ever user whose name is unavailable).
- [ ] `info.title` stays `Trade Lab API — Agent`; only agent paths present; `/agent/query` untouched.
**Depends on:** none (authored first; AGENT-CONTROLLER-1 consumes the generated DTO)

---

## Frontend

### [AGENT-CLI-1] — Add name to AgentSummaryResponse type

**Layer:** CLI (domains/agent/types + api)
**Domain:** agent (frontend)
**Use case:** chat-with-ai-assistant (Flow I / #208)
**Implements:** mirrors AGENT-API-CONTRACT-1
**Inputs:** `AgentSummaryResponse` schema (with `name`)
**Outputs:** Updated `AgentSummaryResponse` interface in `domains/agent/types/agent.ts`
**Acceptance criteria:**
- [ ] `AgentSummaryResponse` gains `name: string | null`.
- [ ] `fetchConversationSummary` continues to return the full typed object (no shape change beyond the type).
- [ ] No `any`; types compile; existing `fetchConversationSummary` test kept green.
**Depends on:** AGENT-API-CONTRACT-1

### [AGENT-STATE-1] — Surface name from useConversationSummary

**Layer:** STATE (domains/agent/hooks)
**Domain:** agent (frontend)
**Use case:** chat-with-ai-assistant (Flow I / #208)
**Implements:** agent-assistant-chat Flow G — step 4; Flow I — step 6
**Inputs:** `AgentSummaryResponse` (summary, name)
**Outputs:** `useConversationSummary` returns `name: string | null` alongside `summary`
**Acceptance criteria:**
- [ ] `UseConversationSummaryResult` gains `name: string | null` (`query.data?.name ?? null`).
- [ ] A read failure resolves both `summary` and `name` to null, never surfaced as an error (existing `retry: false`, `enabled: isOpen` preserved).
- [ ] Hook tests cover: name present / summary present / both null / read-error.
**Depends on:** AGENT-CLI-1

### [AGENT-COMP-1] — Build & seed "Hello {firstToken}" greeting

**Layer:** COMP (domains/agent/components)
**Domain:** agent (frontend)
**Use case:** chat-with-ai-assistant (Flow I / #208)
**Implements:** agent-assistant-chat Flow F — step 3; Flow G — steps 4–5; Flow I — steps 6–7; use case failure "No prior summary"
**Inputs:** `summary`, `name` from `useConversationSummary`; store `seedSummaryGreeting`, `suppressSummaryGreeting`, `transcript`, `isOpen`
**Outputs:** Updated opening-greeting effect in `AssistantChatPopup`
**Acceptance criteria:**
- [ ] Open + empty transcript + `summary` present + `!suppressSummaryGreeting` → seed summary (unchanged).
- [ ] Open + empty transcript + no `summary` + `name` present → seed greeting `"Hello " + name.trim().split(/\s+/)[0]`, INCLUDING on the blank "New conversation" view (the name greeting is NOT gated by `suppressSummaryGreeting`).
- [ ] Open + empty transcript + no `summary` + no `name` → stays empty.
- [ ] "New conversation" while a summary exists → stays blank (summary suppressed; `name` is null from the backend).
- [ ] Flow F/G/I code comments updated to reflect the name-greeting fallback.
- [ ] Component tests: first-token greeting from a multi-word name, new-conversation-no-summary greeting, summary greeting unchanged, both-absent empty.
**Depends on:** AGENT-STATE-1

---

## Dependency summary

| Task | Title | Depends on |
|------|-------|-----------|
| USER-API-1 | Expose `getName` on `UserLookupApi` | none |
| USER-SVC-1 | Implement `getName` in `UserService` | USER-API-1 |
| AGENT-SVC-1 | `AgentUserProfileService` resolve & cache name | USER-API-1 (runtime: USER-SVC-1) |
| AGENT-SVC-2 | Name available to Main Agent per query | AGENT-SVC-1 |
| AGENT-CONFIG-1 | Main Agent instruction addresses by name | AGENT-SVC-2 |
| AGENT-API-CONTRACT-1 | Add `name` to `AgentSummaryResponse` | none |
| AGENT-CONTROLLER-1 | Return `name` from `GET /agent/summary` | AGENT-API-CONTRACT-1, AGENT-SVC-1 |
| AGENT-CLI-1 | Add `name` to FE type | AGENT-API-CONTRACT-1 |
| AGENT-STATE-1 | Surface `name` from hook | AGENT-CLI-1 |
| AGENT-COMP-1 | Build & seed greeting in popup | AGENT-STATE-1 |
