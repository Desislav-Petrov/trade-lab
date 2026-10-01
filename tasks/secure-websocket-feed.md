# Task List: Secure the Market-Data WebSocket with JWT

> Source: GitHub issue #201 — "Protect WS communication with token".
> Nature: security hardening of an existing flow (`market-data-websocket-feed` Flow A, step 2). No new use case doc, no model change.
>
> **Revision (PR #202 review):** JWT validation is no longer exposed via a cross-domain `user.api.TokenValidationApi`. Instead, `JwtService` moves into a new shared-kernel **`common`** domain that any domain may import directly. Market Data imports `JwtService` from `common` directly. See COMMON-1 and DOCS-5.
>
> **Locked decisions (from issue #201 sparring):**
> 1. Reuse the existing internal JWT; transport it via the `Sec-WebSocket-Protocol` subprotocol header (browsers cannot set `Authorization` on a native WebSocket). No new credential.
> 2. Identity derived **solely** from the token `sub` claim. The `userId` query param is removed.
> 3. Connection is validated **once** at handshake and lives until the user navigates away, even if the token's `exp` passes ("let it live"; expiry enforcement is future hardening).
>
> **Key implementation constraints (grounded, not invented):**
> - `JwtService` is a cross-cutting platform tool, not user business logic. It lives in a shared-kernel `common` domain. `common` depends on no other domain; any domain may import it directly — the single documented exception to the "no cross-domain `service` imports" rule.
> - Validation runs **post-upgrade** in `afterConnectionEstablished` (reading the token from `session.handshakeHeaders["Sec-WebSocket-Protocol"]`) to preserve the existing WS close code `4401` and the frontend `onError(code)` handling. A pre-upgrade handshake rejection would yield HTTP 401 / close `1006` and break that.
> - Browsers abort the upgrade unless the server echoes an accepted subprotocol. Client sends `['bearer', <token>]`; server declares `bearer` as supported and echoes it, reading the token from the second value.
>
> **No API-CONTRACT task:** the feed is a raw WebSocket, not represented in any `*-openapi.yaml`. This use case introduces no REST endpoint.

---

## DOCS (prerequisite — must be done first; all code tasks are grounded in these)

### [DOCS-1] — Write decision log entry for WebSocket JWT authentication

**Layer:** Docs (decision log)
**Domain:** common / marketdata / cross-cutting security
**Use case:** secure-websocket-feed
**Implements:** issue #201 — the three locked decisions
**Inputs:** issue #201; confirmed decisions; PR #202 review (shared-kernel approach)
**Outputs:** decisions/2026-10-01-websocket-jwt-authentication.md
**Acceptance criteria:**
- [ ] Records decision: reuse existing internal JWT, transported via `Sec-WebSocket-Protocol` (option b), not a new credential
- [ ] Records decision: identity derived solely from token `sub`; `userId` query param removed
- [ ] Records decision: post-upgrade validation to preserve close code `4401`; "let it live" on mid-connection expiry (future hardening noted)
- [ ] Records decision: `JwtService` lives in a shared-kernel `common` domain imported directly by Market Data (supersedes the earlier `user.api.TokenValidationApi` option)
**Depends on:** none

### [DOCS-2] — Update market-data-websocket-feed flow for JWT handshake auth

**Layer:** Docs (domain/flows)
**Domain:** marketdata
**Use case:** secure-websocket-feed
**Implements:** market-data-websocket-feed — Flow A steps 1–2, Flow A Error Cases, Domain Models (Session)
**Inputs:** domain/flows/market-data-websocket-feed.md
**Outputs:** updated domain/flows/market-data-websocket-feed.md
**Acceptance criteria:**
- [ ] Overview + Flow A step 1: connection URL is tokenless; JWT sent via `Sec-WebSocket-Protocol` subprotocol header
- [ ] Flow A step 2 rewritten: validate JWT (signature/`exp`/`iss`) via the shared `JwtService`; derive `userId` from `sub`; reject `4401` on missing/malformed/invalid/expired token
- [ ] Error Cases table replaces "missing/unknown userId" with token-based failures → `4401`
- [ ] Domain Models → Session: `userId` sourced from validated token `sub`, not query param
- [ ] References DOCS-1 decision log entry
**Depends on:** DOCS-1

### [DOCS-3] — Align market-data-feed-routing userId source

**Layer:** Docs (domain/flows)
**Domain:** marketdata
**Use case:** secure-websocket-feed
**Implements:** market-data-feed-routing — Flow A step 1, Domain Models (Session)
**Inputs:** domain/flows/market-data-feed-routing.md
**Outputs:** updated domain/flows/market-data-feed-routing.md
**Acceptance criteria:**
- [ ] Flow A step 1: `userId` read from validated token, not the query parameter
- [ ] Domain Models → Session note corrected accordingly
**Depends on:** DOCS-2

### [DOCS-4] — Note WS handshake reuse in jwt-authentication flow

**Layer:** Docs (domain/flows)
**Domain:** cross-cutting security
**Use case:** secure-websocket-feed
**Implements:** jwt-authentication — Notes
**Inputs:** domain/flows/jwt-authentication.md
**Outputs:** updated domain/flows/jwt-authentication.md
**Acceptance criteria:**
- [ ] Adds note: the same JWT validation (`JwtService`, now in the `common` domain) is reused for the WS handshake, with the token arriving via `Sec-WebSocket-Protocol` (browsers can't set `Authorization` on a native WS), validated once at handshake rather than per message
**Depends on:** DOCS-2

### [DOCS-5] — Document the `common` shared-kernel domain in architecture

**Layer:** Docs (standards/architecture)
**Domain:** common
**Use case:** secure-websocket-feed
**Implements:** PR #202 review — "describe the domain in the documentation"
**Inputs:** standards/architecture.md
**Outputs:** updated standards/architecture.md
**Acceptance criteria:**
- [ ] Adds `common` to the Domain Taxonomy as a shared kernel of cross-cutting tools (first occupant: `JwtService`)
- [ ] States explicitly that `common` depends on no other domain and that any domain may import directly from `common.*` — the single documented exception to the "no cross-domain `service` imports" rule
- [ ] Backend package structure section mentions `common/` alongside the other top-level domain packages
**Depends on:** DOCS-1

---

## Backend — `common` shared kernel

### [COMMON-1] — Move JwtService (and InvalidTokenException) into the `common` domain

**Layer:** Service (common.service / common.exception)
**Domain:** common
**Use case:** secure-websocket-feed
**Implements:** jwt-authentication (reused by WS); PR #202 review
**Inputs:** existing `user.service.JwtService`, `user.exception.InvalidTokenException`, and all their references
**Outputs:** `org.dpp.tradelab.common…JwtService`, `org.dpp.tradelab.common.exception.InvalidTokenException`; all imports updated; tests moved
**Acceptance criteria:**
- [ ] `JwtService` moved to `common` with its public API unchanged (`issueToken`, `validateAndExtractUserId`)
- [ ] `InvalidTokenException` moved to `common.exception` (thrown by `JwtService`; must not create a `common → user` dependency)
- [ ] All references updated: `JwtAuthenticationFilter`, `OidcAuthService`, `UserService`, `OidcAuthenticationSuccessHandler`, and all tests that autowire/mock `JwtService` or reference `InvalidTokenException`
- [ ] The previously-added `user.api.TokenValidationApi` + `TokenValidationApiImpl` are deleted (superseded)
- [ ] `common` imports nothing from any other domain
- [ ] Existing `JwtService` unit test moved and passing
**Depends on:** DOCS-5

---

## Backend — WebSocket config & handler (`marketdata`)

### [CONTROLLER-1] — Accept token-bearing subprotocol in WS config

**Layer:** Controller (marketdata.controller — MarketDataWebSocketConfig)
**Domain:** marketdata
**Use case:** secure-websocket-feed
**Implements:** market-data-websocket-feed Flow A step 1 (handshake transport)
**Inputs:** existing MarketDataWebSocketConfig
**Outputs:** updated MarketDataWebSocketConfig declaring the `bearer` subprotocol marker
**Acceptance criteria:**
- [ ] Handshake negotiates/echoes the fixed `bearer` subprotocol so browsers complete the upgrade when `['bearer', token]` is requested
- [ ] Existing allowed-origin / CORS behaviour preserved unchanged
- [ ] No `userId` handling introduced here
**Depends on:** DOCS-2

### [SVC-2] — Authenticate WS handshake via JWT in the handler

**Layer:** Service (marketdata.service — MarketDataWebSocketHandler)
**Domain:** marketdata
**Use case:** secure-websocket-feed
**Implements:** market-data-websocket-feed Flow A step 2 + Error Cases
**Inputs:** WebSocketSession (handshakeHeaders); `common…JwtService` (imported directly)
**Outputs:** updated MarketDataWebSocketHandler; updated handler tests
**Acceptance criteria:**
- [ ] Injects `common…JwtService` directly (no `user.api` interface)
- [ ] `afterConnectionEstablished` reads the JWT from `Sec-WebSocket-Protocol` handshake header (second value after `bearer`)
- [ ] Validates via `JwtService.validateAndExtractUserId`; derives `userId` from `sub`; stores `userId` in `session.attributes`
- [ ] Closes `4401` on missing/malformed/invalid/expired token; closes `4500` on snapshot errors (unchanged)
- [ ] `afterConnectionClosed` reads `userId` from `session.attributes` (no query parsing)
- [ ] All `?userId=` query extraction removed
- [ ] Handler tests updated: valid token → registers + snapshot; missing/invalid/expired token → `4401`; no query-param reliance
**Depends on:** COMMON-1, CONTROLLER-1

### [CONTROLLER-2] — Refresh SecurityConfig WS comment/matcher intent

**Layer:** Controller (config — SecurityConfig)
**Domain:** cross-cutting security
**Use case:** secure-websocket-feed
**Implements:** market-data-websocket-feed Flow A step 2
**Inputs:** existing SecurityConfig
**Outputs:** updated SecurityConfig
**Acceptance criteria:**
- [ ] `/api/v1/market-data/feed` remains permitAll at the servlet-filter level (auth now occurs in the handshake, token in subprotocol — not the Bearer servlet filter)
- [ ] Stale comment ("no JWT on WS handshake, userId in query param") corrected to describe subprotocol-based handshake auth
**Depends on:** SVC-2

---

## Frontend — CLI & STATE (`marketdata`)  [separate FE PR — context only]

### [CLI-1] — Send JWT via subprotocol in the feed client

**Layer:** CLI (marketdata/api — marketDataFeedApi.ts)
**Depends on:** DOCS-2

### [STATE-1] — Supply the token from the session store to the feed hook

**Layer:** State (marketdata/hooks — useMarketDataFeed.ts)
**Depends on:** CLI-1

---

## Dependency summary

| Task | Title | Depends on |
|------|-------|-----------|
| DOCS-1 | Decision log entry | none |
| DOCS-2 | Update websocket-feed flow | DOCS-1 |
| DOCS-3 | Align feed-routing flow | DOCS-2 |
| DOCS-4 | Note reuse in jwt-authentication flow | DOCS-2 |
| DOCS-5 | Document `common` shared-kernel domain | DOCS-1 |
| COMMON-1 | Move `JwtService` into `common` | DOCS-5 |
| CONTROLLER-1 | Accept subprotocol in WS config | DOCS-2 |
| SVC-2 | Authenticate handshake in handler | COMMON-1, CONTROLLER-1 |
| CONTROLLER-2 | Refresh SecurityConfig comment | SVC-2 |
| CLI-1 | Send JWT via subprotocol | DOCS-2 |
| STATE-1 | Supply token from session store | CLI-1 |
