# Tasks: Chat with the AI Assistant — Summary-Based Durable Persistence

**Use case:** `chat-with-ai-assistant` (persistence extension)
**Flows:** `domain/flows/agent-assistant-chat.md` — Flow B (per-turn summary write), Flow C (restart → summary continuity), Flow D (reload → summary greeting), Flow F (Start a New Conversation), Flow G (Resume with Summary Greeting), Flow H (Regenerate Conversation Summary)
**Models:** `domain/model/conversation-summary.md` (durable, user-scoped `last_conversation`), `domain/model/agent-conversation.md` (ephemeral session)
**Decision:** `decisions/2026-10-10-agent-conversation-summary-continuity.md`
**Issue:** #207

---

## Confirmed design decisions (resolve before execution)

These four decisions were agreed during decomposition and are binding for every task below:

1. **Persistence & migration mechanism.** No Flyway/Liquibase exists in this repo. Use JPA `@Entity` classes in `agent.model` + Spring Data repositories + Hibernate `ddl-auto` (`update` dev / `validate` prod), exactly like every other entity. Store ADK session state and serialized ADK events as **portable JSON text** columns (work on both H2 and PostgreSQL) — **not** native `JSONB`. Do **not** introduce a migration tool or JSONB; either would require its own decision-log entry.
2. **Concurrency test DB.** Implement row-level locking (`PESSIMISTIC_WRITE` / `SELECT … FOR UPDATE`) in the service. Keep H2 for normal unit/repository tests. Add **Testcontainers PostgreSQL** for the atomicity/concurrency integration tests only (one test-scope build dependency).
3. **Summary generation.** Regenerate the `last_conversation` summary via the existing agent model (Gemini) with a dedicated summarisation prompt, after each completed turn, written to ADK **user-scoped** state key `user:last_conversation`. Not a heuristic/truncation.
4. **Greeting transport.** New endpoint `GET /api/v1/agent/summary` → `{ summary: string | null }`, read from user-scoped state. The frontend calls it on popup open (Flow G) and renders the summary as the opening assistant message. Kept out of the SSE stream.

## Standing notes / known drift

- Backend root is `services/backend/` (the `services/back-end` spelling in `standards/backend.md` is stale). Package root `org.dpp.tradelab`. Agent domain already has `controller/`, `exception/`, `service/`, `tool/`; this work adds `model/` and `repository/`.
- Agent contract is `services/contract/agent-openapi.yaml` (per-domain). The single-file `trade-lab-openapi.yaml` reference in `standards/frontend.md`/`architecture.md` is pre-existing doc drift — not fixed here.
- The **exact `com.google.adk.sessions.BaseSessionService` method surface depends on the pinned ADK Java version**. The engineer must read the ADK version's interface and implement every abstract method it declares. Do not assume signatures from another ADK language/version.
- Scope per the decision log: build the `SessionService` to satisfy the ADK `BaseSessionService` contract (sessions, events, state persistence), but drive the **product** behaviour from the durable user-scoped summary. Full verbatim-history *features* are out of scope; event persistence exists only to satisfy contract correctness.
- Existing wiring to change: `org.dpp.tradelab.config.AgentConfiguration` (bean `agentSessionService` currently returns `InMemorySessionService`; `agentRunner` consumes it) and `org.dpp.tradelab.agent.service.AgentService` (constructor currently injects the concrete `InMemorySessionService`).

---

# BACKEND

## Database (DB)

### [DB-1] — AgentSession JPA entity

**Layer:** Database
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Flow C step 3 (session load), Flow H (state persistence); issue #207 Session model
**Inputs:** ADK `Session` shape (appName, userId, sessionId, state map, lastUpdateTime); `standards/backend.md` — ORM entity template
**Outputs:** `org.dpp.tradelab.agent.model.AgentSession` entity mapped to table `agent_sessions`
**Acceptance criteria:**
- [ ] Fields: `id: UUID` (PK, pre-assigned), `appName: String`, `userId: String`, `sessionId: String`, `stateJson: String` (portable JSON text, session-scoped state), `createdAt`, `updatedAt` (`@UpdateTimestamp`).
- [ ] Unique constraint on `(appName, userId, sessionId)`; index on `(appName, userId, sessionId)`.
- [ ] Plain `class` implementing `Persistable<UUID>` with `@Transient _isNew`; manual `equals`/`hashCode`/`toString` on `id` only; no `@GeneratedValue`.
- [ ] `stateJson` is a text/CLOB column — no JSONB.
**Depends on:** none

### [DB-2] — AgentEvent JPA entity

**Layer:** Database
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Flow B (append turn / appendEvent), issue #207 Event model
**Inputs:** ADK `Event` shape; `standards/backend.md` — ORM entity template
**Outputs:** `org.dpp.tradelab.agent.model.AgentEvent` entity mapped to table `agent_events`
**Acceptance criteria:**
- [ ] Fields: `id: UUID` (PK, pre-assigned), `appName: String`, `userId: String`, `sessionId: String`, `eventJson: String` (portable JSON text — serialized ADK event), `eventTimestamp: Instant`, `sequence: Long` (monotonic ordering within a session), `createdAt`.
- [ ] Index on `(appName, userId, sessionId, sequence)` for ordered retrieval.
- [ ] Plain `class` implementing `Persistable<UUID>` per the entity template; no JSONB.
**Depends on:** none

### [DB-3] — AgentUserState JPA entity (user-scoped state)

**Layer:** Database
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** `domain/model/conversation-summary.md`; Flow H (write `user:last_conversation`), Flow G (read)
**Inputs:** ADK user-scoped state convention (`user:` prefixed keys, keyed by appName+userId); `standards/backend.md`
**Outputs:** `org.dpp.tradelab.agent.model.AgentUserState` entity mapped to table `agent_user_state`
**Acceptance criteria:**
- [ ] Fields: `id: UUID` (PK, pre-assigned), `appName: String`, `userId: String`, `stateJson: String` (portable JSON text holding user-scoped keys incl. `last_conversation`), `updatedAt` (`@UpdateTimestamp`).
- [ ] Unique constraint on `(appName, userId)`.
- [ ] Plain `class` implementing `Persistable<UUID>` per the entity template; no JSONB.
**Depends on:** none

## Repository (REPO)

### [REPO-1] — AgentSessionRepository (with pessimistic-lock finder)

**Layer:** Repository
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Flow H (atomic appendEvent under row lock), Flow C (session load), issue #207 concurrency
**Inputs:** `AgentSession` entity (DB-1)
**Outputs:** `org.dpp.tradelab.agent.repository.AgentSessionRepository : JpaRepository<AgentSession, UUID>`
**Acceptance criteria:**
- [ ] `findByAppNameAndUserIdAndSessionId(...)` returning `AgentSession?`/`Optional`.
- [ ] A `@Lock(PESSIMISTIC_WRITE)` variant (`SELECT … FOR UPDATE`) used by appendEvent to lock the session row.
- [ ] `findAllByAppNameAndUserId(...)` for list-sessions; `deleteByAppNameAndUserIdAndSessionId(...)` for delete.
- [ ] Repository test (`@SpringBootTest` + H2) covers the custom finders only.
**Depends on:** DB-1

### [REPO-2] — AgentEventRepository

**Layer:** Repository
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Flow B (append / retrieve events in order), issue #207 event lookup
**Inputs:** `AgentEvent` entity (DB-2)
**Outputs:** `org.dpp.tradelab.agent.repository.AgentEventRepository : JpaRepository<AgentEvent, UUID>`
**Acceptance criteria:**
- [ ] `findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc(...)`.
- [ ] A finder/aggregate to compute the next `sequence` for a session (e.g. max sequence) — or documented strategy.
- [ ] `deleteAllByAppNameAndUserIdAndSessionId(...)` for session deletion.
- [ ] Repository test (H2) covers ordering and delete-by-session.
**Depends on:** DB-2

### [REPO-3] — AgentUserStateRepository (with pessimistic-lock finder)

**Layer:** Repository
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Flow H (write), Flow G (read) of `user:last_conversation`
**Inputs:** `AgentUserState` entity (DB-3)
**Outputs:** `org.dpp.tradelab.agent.repository.AgentUserStateRepository : JpaRepository<AgentUserState, UUID>`
**Acceptance criteria:**
- [ ] `findByAppNameAndUserId(...)` returning `AgentUserState?`/`Optional`.
- [ ] A `@Lock(PESSIMISTIC_WRITE)` variant for concurrent-safe read-modify-write of user state.
- [ ] Repository test (H2) covers the custom finders.
**Depends on:** DB-3

## Exception (EXCEPTION)

### [EXCEPTION-1] — SessionPersistenceException

**Layer:** Exception
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Error handling for serialization/persistence failures in the session service and summary service
**Inputs:** `standards/backend.md` — Error Handling
**Outputs:** `org.dpp.tradelab.agent.exception.SessionPersistenceException`
**Acceptance criteria:**
- [ ] Typed domain exception with a message and optional cause.
- [ ] Thrown from the service layer when (de)serialization or a persistence write fails; JPA exceptions are caught and rethrown as this type (never propagated raw).
- [ ] Mapped in `GlobalExceptionHandler` to a 500 error response shape (or documented existing mapping).
**Depends on:** none

## Service (SVC)

### [SVC-1] — ADK JSON serialization component

**Layer:** Service
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Serialization requirement of issue #207; DB-2/DB-3 JSON columns
**Inputs:** ADK `Event` and session `state` (`Map<String, Any>`); Jackson; `AdkJsonSerializer` target in `agent.service` (or `agent.util`)
**Outputs:** `AdkJsonSerializer` with `serializeEvent/deserializeEvent` and `serializeState/deserializeState`
**Acceptance criteria:**
- [ ] Serializes an ADK `Event` to portable JSON text and reconstructs an equivalent `Event` (round-trip) compatible with the pinned ADK version.
- [ ] Serializes/deserializes the session/user state map to/from JSON text.
- [ ] Never serializes non-serializable runtime infrastructure (connections, services) — only data state. Rejects/omits such values safely.
- [ ] On failure throws `SessionPersistenceException`.
- [ ] Unit tests cover round-trip for events and state, and the failure path.
**Depends on:** EXCEPTION-1

### [SVC-2] — PersistentSessionService (ADK BaseSessionService implementation)

**Layer:** Service
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** issue #207 core; Flow B (appendEvent), Flow C (getSession), Flow H (atomic state update under lock)
**Inputs:** `BaseSessionService` interface of the pinned ADK version; REPO-1/2/3; SVC-1; `AGENT_APP_NAME`
**Outputs:** `org.dpp.tradelab.agent.service.PersistentSessionService : BaseSessionService` (Spring `@Service`/bean)
**Acceptance criteria:**
- [ ] Implements **every** abstract method of the pinned ADK `BaseSessionService` (create / get / list / delete session; append/persist event; persist session-state updates; honour the event filtering/config args the ADK API declares — e.g. recent-events/after-timestamp — rather than always loading full history).
- [ ] `getSession` reconstructs a `Session` from `AgentSession` + ordered `AgentEvent` rows + merged session-scoped and `user:`-scoped state.
- [ ] `appendEvent` is `@Transactional`: acquires the `PESSIMISTIC_WRITE` lock on the session row, persists the event, updates session state and user-scoped state, and the session timestamp — all-or-nothing. Concurrent appends to the same session do not lose updates.
- [ ] User-scoped (`user:` prefixed) state is persisted via `AgentUserState` and shared across that user's sessions; session-scoped state via `AgentSession`.
- [ ] JPA exceptions are caught and rethrown as `SessionPersistenceException`.
- [ ] Unit tests (mocked repos) cover each method + the transactional/lock path logic.
- [ ] Integration test against **Testcontainers PostgreSQL** covers: create+retrieve; state survives a new service instance; appended events retrievable after new instance; multi-event ordering; delete removes data; list returns expected sessions; atomicity (failed tx leaves no partial state); concurrent same-session updates lose nothing; independent per-user sessions.
**Depends on:** REPO-1, REPO-2, REPO-3, SVC-1

### [SVC-3] — ConversationSummaryService

**Layer:** Service
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** `domain/model/conversation-summary.md`; Flow H (regenerate + write), Flow G step 3 (read)
**Inputs:** agent model (Gemini bean) or a dedicated summariser prompt; the session/user-state write path (SVC-2 / REPO-3); `AGENT_APP_NAME`
**Outputs:** `org.dpp.tradelab.agent.service.ConversationSummaryService` with `regenerate(userId, conversationId)` and `getSummary(userId): String?`
**Acceptance criteria:**
- [ ] `regenerate` builds a condensed natural-language summary of the current conversation via the agent model and writes it to user-scoped state `user:last_conversation` for the user, refreshing its timestamp.
- [ ] `getSummary` returns the current `last_conversation` for the user, or `null` when none exists (first-ever conversation).
- [ ] Writes are concurrency-safe (uses the SVC-2 locked state path / REPO-3 lock).
- [ ] A summary write/generation failure does not propagate to break the user reply: logged, previous value retained (Flow H / Flow B error row). `getSummary` read failure returns `null` without surfacing an error (Flow G error row).
- [ ] `userId` is always the caller-supplied value (JWT-resolved upstream) — never from a request body.
- [ ] Unit tests cover regenerate happy path, getSummary present/absent, and both failure paths.
**Depends on:** SVC-2

### [SVC-4] — Integrate per-turn summary regeneration into AgentService

**Layer:** Service
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Flow B step 11 / Flow C step 6 (regenerate summary after reply completes); session-service type swap to the ADK interface
**Inputs:** existing `AgentService` (currently injects concrete `InMemorySessionService`); SVC-2; SVC-3
**Outputs:** Updated `AgentService` depending on the ADK `BaseSessionService` abstraction and triggering `ConversationSummaryService.regenerate(...)` on stream completion
**Acceptance criteria:**
- [ ] `AgentService` constructor injects the ADK session-service **interface/base type** (not the concrete `InMemorySessionService`), so the persistent bean is used.
- [ ] After the reply stream completes successfully, `ConversationSummaryService.regenerate(userId, conversationId)` is invoked (e.g. on stream `doOnComplete`) without blocking or corrupting token delivery.
- [ ] A regeneration failure never fails the user-facing stream (matches Flow B "Summary regeneration fails" row).
- [ ] Existing `AgentService` behaviour (routing, SSE dedup, timeout, error mapping) is unchanged; existing tests updated to the new session-service type and still pass.
**Depends on:** SVC-2, SVC-3

## API Contract (API-CONTRACT)

> Ordering note: the controller implements a delegate **generated from** this contract, so per this repo's established pattern the contract task precedes the controller task.

### [API-CONTRACT-1] — Add `GET /agent/summary` to the agent OpenAPI contract

**Layer:** OpenAPI Contract
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Flow G (Resume with Summary Greeting); decision #4 (greeting transport)
**Inputs:** `services/contract/agent-openapi.yaml`; new operation `GET /agent/summary`
**Outputs:** Updated `agent-openapi.yaml` with the new path + response schema
**Acceptance criteria:**
- [ ] New path `GET /agent/summary`, `operationId: getConversationSummary`, tag `Agent`.
- [ ] 200 response schema `AgentSummaryResponse` with nullable `summary: string` (null ⇒ no prior summary).
- [ ] 401 response reuses the existing `ErrorResponse` schema.
- [ ] `info.title` remains `Trade Lab API — Agent`; only agent paths present; existing `/agent/query` path untouched (append, never overwrite).
- [ ] `./gradlew openApiGenerate` regenerates `AgentApiDelegate` with the new method.
**Depends on:** none

## Controller (CONTROLLER)

### [CONTROLLER-1] — Implement `GET /agent/summary` in the agent delegate

**Layer:** Controller
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Flow G steps 3–4 (read durable summary, return greeting)
**Inputs:** generated `AgentApiDelegate` (from API-CONTRACT-1); `ConversationSummaryService` (SVC-3); JWT `userId` resolution (as used by the existing query delegate)
**Outputs:** `getConversationSummary` implemented in `org.dpp.tradelab.agent.controller.AgentApiDelegateImpl`
**Acceptance criteria:**
- [ ] Resolves `userId` server-side from the JWT (`SecurityContextHolder`) — never from the request.
- [ ] Returns 200 with `{ summary }` = `ConversationSummaryService.getSummary(userId)` (may be `null`).
- [ ] Returns 401 when unauthenticated, consistent with the existing delegate.
- [ ] `@SpringBootTest` + MockMvc tests cover: summary present, summary null, and unauthenticated.
**Depends on:** API-CONTRACT-1, SVC-3

## Config (CONFIG — extension; no canonical layer)

### [CONFIG-1] — Replace InMemorySessionService with the persistent bean

**Layer:** Config (extension — not in the canonical layer table)
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** issue #207 acceptance ("can replace InMemorySessionService through configuration/wiring"); decision log
**Inputs:** `org.dpp.tradelab.config.AgentConfiguration`; `PersistentSessionService` (SVC-2)
**Outputs:** Updated `AgentConfiguration` wiring the persistent session service into the ADK `Runner`
**Acceptance criteria:**
- [ ] The `agentSessionService` bean (or the `Runner`'s `sessionService(...)`) resolves to `PersistentSessionService`; `InMemorySessionService` is no longer wired into the runner.
- [ ] `Runner` still uses `AGENT_APP_NAME` and the existing `RunConfig` (SSE, `autoCreateSession(false)`).
- [ ] Agent business logic (agents, tool, prompts) is unchanged.
- [ ] `AgentConfigurationTest` updated to assert the persistent bean type; full `./gradlew build` passes.
**Depends on:** SVC-2

---

# FRONTEND

## API Client (CLI)

### [CLI-1] — `fetchConversationSummary` client + types

**Layer:** API Client
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Flow G step 2 (request opening summary)
**Inputs:** `services/contract/agent-openapi.yaml` `GET /agent/summary` (from API-CONTRACT-1); shared Axios instance; `agent/types/agent.ts`
**Outputs:** `fetchConversationSummary()` in `src/domains/agent/api/`, typed `{ summary: string | null }`, plus a TanStack query-key constant
**Acceptance criteria:**
- [ ] Calls `GET /api/v1/agent/summary` via the shared Axios instance (JWT attached by the existing interceptor).
- [ ] Request/response types defined in `agent/types/`; no `any`.
- [ ] Query-key constant exported from `agent/api/`.
- [ ] Vitest test mocks Axios and covers summary-present and summary-null responses.
**Depends on:** API-CONTRACT-1

## State (STATE)

### [STATE-1] — Extend `useAgentStore` for new conversation + summary greeting

**Layer:** State
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Flow F step 2 (reset), Flow G step 5 (render greeting), Flow D step 5 (recover via summary)
**Inputs:** existing `src/domains/agent/hooks/useAgentStore.ts`
**Outputs:** `startNewConversation()` and a summary-greeting seeding action on the store
**Acceptance criteria:**
- [ ] `startNewConversation()` clears `transcript` and `conversationId` but keeps `isOpen = true` (distinct from `reset()`).
- [ ] An action to seed an opening assistant turn from a summary string (only when the transcript is empty), rendered as an assistant message.
- [ ] Existing actions/behaviour unchanged; store remains non-persisted (transcript still lost on reload, per Flow D).
- [ ] Vitest tests cover `startNewConversation` and summary-greeting seeding.
**Depends on:** none

### [STATE-2] — `useConversationSummary` query hook

**Layer:** State
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Flow G steps 2–4 (fetch summary on open)
**Inputs:** `fetchConversationSummary` (CLI-1); TanStack Query
**Outputs:** `useConversationSummary()` hook exposing `summary`, loading, and a refetch
**Acceptance criteria:**
- [ ] `useQuery` keyed by the CLI-1 constant; fetches when the popup opens; exposes `summary: string | null`.
- [ ] Exposes a refetch used after "New conversation" to re-pull the carried-forward summary.
- [ ] Read failure resolves to no greeting (summary treated as null) — no error surfaced (Flow G error row).
- [ ] Vitest tests (mocked CLI) cover present, null, and error → null.
**Depends on:** CLI-1

## Component (COMP)

### [COMP-1] — "New conversation" button in the chat popup

**Layer:** Component
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Flow F steps 1–3 (start new conversation)
**Inputs:** `src/domains/agent/components/AssistantChatPopup.tsx`; `useAgentStore` (STATE-1); `useConversationSummary` (STATE-2); Shadcn `Button`
**Outputs:** A "New conversation" control in the popup
**Acceptance criteria:**
- [ ] Button calls `startNewConversation()` then refetches the summary so the popup reopens on the carried-forward summary greeting.
- [ ] Previous transcript is cleared and a fresh `conversationId` is generated on the next send (existing `ensureConversationId` path).
- [ ] Uses the Shadcn `Button` from `shared/components/ui/` and `cn()`; no raw Radix.
- [ ] Vitest test asserts the click resets state and triggers the summary refetch.
**Depends on:** STATE-1, STATE-2

### [COMP-2] — Render the opening summary greeting

**Layer:** Component
**Domain:** agent
**Use case:** chat-with-ai-assistant-persistence
**Implements:** Flow G step 5 / Flow A step 3 (present summary on open); Flow D step 5
**Inputs:** `AssistantChatPopup.tsx`; `useConversationSummary` (STATE-2); `useAgentStore` (STATE-1); existing `AssistantMessage`
**Outputs:** Opening summary rendered as the first assistant message when the popup opens with an empty transcript and a non-null summary
**Acceptance criteria:**
- [ ] On open, when `transcript` is empty and `summary` is non-null, the summary is shown as an opening assistant message (via the store greeting seed) using the existing `AssistantMessage` renderer.
- [ ] When `summary` is null (first-ever conversation) the popup opens empty with no greeting (Flow G "No prior summary" row).
- [ ] No duplicate greeting once the user starts sending; greeting does not overwrite real turns.
- [ ] Vitest tests cover: greeting shown when summary present, no greeting when null.
**Depends on:** STATE-1, STATE-2

---

## Dependency summary

| Task | Title | Depends on |
|------|-------|-----------|
| DB-1 | AgentSession entity | none |
| DB-2 | AgentEvent entity | none |
| DB-3 | AgentUserState entity | none |
| REPO-1 | AgentSessionRepository (locking) | DB-1 |
| REPO-2 | AgentEventRepository | DB-2 |
| REPO-3 | AgentUserStateRepository (locking) | DB-3 |
| EXCEPTION-1 | SessionPersistenceException | none |
| SVC-1 | AdkJsonSerializer | EXCEPTION-1 |
| SVC-2 | PersistentSessionService (BaseSessionService) | REPO-1, REPO-2, REPO-3, SVC-1 |
| SVC-3 | ConversationSummaryService | SVC-2 |
| SVC-4 | AgentService integration + type swap | SVC-2, SVC-3 |
| API-CONTRACT-1 | `GET /agent/summary` contract | none |
| CONTROLLER-1 | `getConversationSummary` delegate | API-CONTRACT-1, SVC-3 |
| CONFIG-1 | Swap in persistent session service | SVC-2 |
| CLI-1 | fetchConversationSummary + types | API-CONTRACT-1 |
| STATE-1 | useAgentStore new-conversation + greeting | none |
| STATE-2 | useConversationSummary hook | CLI-1 |
| COMP-1 | New conversation button | STATE-1, STATE-2 |
| COMP-2 | Opening summary greeting | STATE-1, STATE-2 |
