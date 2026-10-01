# Task List: Secure the Market-Data WebSocket with JWT

> Source: GitHub issue #201 — "Protect WS communication with token".
> Nature: security hardening of an existing flow (`market-data-websocket-feed` Flow A, step 2). No new use case doc, no model change.
>
> **Locked decisions (from issue #201 sparring):**
> 1. Reuse the existing internal JWT; transport it via the `Sec-WebSocket-Protocol` subprotocol header (browsers cannot set `Authorization` on a native WebSocket). No new credential.
> 2. Identity derived **solely** from the token `sub` claim. The `userId` query param is removed.
> 3. Connection is validated **once** at handshake and lives until the user navigates away, even if the token's `exp` passes ("let it live"; expiry enforcement is future hardening).
>
> **Key implementation constraints (grounded, not invented):**
> - `marketdata` must NOT import `user.service.JwtService` (architecture.md forbids cross-domain `service` imports). Validation is exposed via a new `user.api.TokenValidationApi`, mirroring the existing `UserSettingsApi` that marketdata already consumes.
> - Validation runs **post-upgrade** in `afterConnectionEstablished` (reading the token from `session.handshakeHeaders["Sec-WebSocket-Protocol"]`) to preserve the existing WS close code `4401` and the frontend `onError(code)` handling. A pre-upgrade handshake rejection would yield HTTP 401 / close `1006` and break that.
> - Browsers abort the upgrade unless the server echoes an accepted subprotocol. Client sends `['bearer', <token>]`; server declares `bearer` as supported and echoes it, reading the token from the second value.
>
> **No API-CONTRACT task:** the feed is a raw WebSocket, not represented in any `*-openapi.yaml`. This use case introduces no REST endpoint.

---

## DOCS (prerequisite — must be done first; all code tasks are grounded in these)

### [DOCS-1] — Write decision log entry for WebSocket JWT authentication

**Layer:** Docs (decision log)
**Domain:** marketdata / cross-cutting security
**Use case:** secure-websocket-feed
**Implements:** issue #201 — the three locked decisions
**Inputs:** issue #201; confirmed decisions (subprotocol transport, sub-claim identity, let-it-live expiry)
**Outputs:** decisions/2026-10-01-websocket-jwt-authentication.md
**Acceptance criteria:**
- [ ] Records decision: reuse existing internal JWT, transported via `Sec-WebSocket-Protocol` (option b), not a new credential
- [ ] Records decision: identity derived solely from token `sub`; `userId` query param removed
- [ ] Records decision: post-upgrade validation to preserve close code `4401`; "let it live" on mid-connection expiry (future hardening noted)
- [ ] Records decision: cross-domain validation exposed via `user.api.TokenValidationApi`, not a `user.service` import
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
- [ ] Flow A step 2 rewritten: validate JWT (signature/`exp`/`iss`) via existing JWT machinery; derive `userId` from `sub`; reject `4401` on missing/malformed/invalid/expired token
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
- [ ] Adds note: the same JWT validation is reused for the WS handshake, with the token arriving via `Sec-WebSocket-Protocol` (browsers can't set `Authorization` on a native WS), validated once at handshake rather than per message
**Depends on:** DOCS-2

---

## Backend — API (cross-domain Kotlin interface, `user.api`)

### [API-1] — Expose TokenValidationApi from the user domain

**Layer:** API (backend cross-domain interface — user.api)
**Domain:** user
**Use case:** secure-websocket-feed
**Implements:** jwt-authentication (reused by WS); market-data-websocket-feed Flow A step 2
**Inputs:** none (new interface)
**Outputs:** org.dpp.tradelab.user.api.TokenValidationApi — `fun validateAndExtractUserId(token: String): UUID`
**Acceptance criteria:**
- [ ] Interface declared in user.api, mirroring UserSettingsApi placement/style
- [ ] Single method `validateAndExtractUserId(token: String): UUID`, typed UUID (not String)
- [ ] No JPA/entity/service types leak through the interface signature
**Depends on:** DOCS-2

### [SVC-1] — Implement TokenValidationApi over JwtService

**Layer:** Service (user.api impl / user.service)
**Domain:** user
**Use case:** secure-websocket-feed
**Implements:** jwt-authentication steps 4–5 (validate, extract userId), reused for WS
**Inputs:** TokenValidationApi (API-1); existing JwtService.validateAndExtractUserId
**Outputs:** TokenValidationApiImpl (@Component) delegating to JwtService; unit test
**Acceptance criteria:**
- [ ] Impl mirrors UserSettingsApiImpl pattern (@Component, constructor injection)
- [ ] Delegates to existing JwtService; no re-implementation of JWT parsing
- [ ] Propagates InvalidTokenException on invalid/expired/bad-issuer tokens (no swallowing)
- [ ] Unit test (KoTest + mockito-kotlin): valid token → userId; invalid token → throws
**Depends on:** API-1

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
**Inputs:** WebSocketSession (handshakeHeaders); TokenValidationApi (API-1)
**Outputs:** updated MarketDataWebSocketHandler; updated handler tests
**Acceptance criteria:**
- [ ] `afterConnectionEstablished` reads the JWT from `Sec-WebSocket-Protocol` handshake header (second value after `bearer`)
- [ ] Validates via TokenValidationApi; derives `userId` from `sub`; stores `userId` in `session.attributes`
- [ ] Closes `4401` on missing/malformed/invalid/expired token; closes `4500` on snapshot errors (unchanged)
- [ ] `afterConnectionClosed` reads `userId` from `session.attributes` (no query parsing)
- [ ] All `?userId=` query extraction removed
- [ ] Handler tests updated: valid token → registers + snapshot; missing/invalid/expired token → `4401`; no query-param reliance
**Depends on:** API-1, CONTROLLER-1

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

## Frontend — CLI & STATE (`marketdata`)

### [CLI-1] — Send JWT via subprotocol in the feed client

**Layer:** CLI (marketdata/api — marketDataFeedApi.ts)
**Domain:** marketdata
**Use case:** secure-websocket-feed
**Implements:** market-data-websocket-feed Flow A step 1
**Inputs:** `token: string`, callbacks (onMessage/onError/onClose)
**Outputs:** updated `connectMarketDataFeed`; updated test
**Acceptance criteria:**
- [ ] Signature drops `userId`, accepts `token`; URL is tokenless
- [ ] Opens `new WebSocket(url, ['bearer', token])` (token as subprotocol, never in URL/query)
- [ ] Reconnect path reuses the same token; `onError(code)`/`onClose` behaviour unchanged
- [ ] Test updated: asserts tokenless URL + subprotocols `['bearer', token]`; no `?userId=`
**Depends on:** DOCS-2

### [STATE-1] — Supply the token from the session store to the feed hook

**Layer:** State (marketdata/hooks — useMarketDataFeed.ts)
**Domain:** marketdata
**Use case:** secure-websocket-feed
**Implements:** market-data-websocket-feed Flow A step 1; Session.accessToken reuse
**Inputs:** `accessToken` from the Zustand session store; existing hook inputs
**Outputs:** updated useMarketDataFeed; updated test
**Acceptance criteria:**
- [ ] Hook reads `accessToken` from the session store and passes it to `connectMarketDataFeed`
- [ ] Connection guarded on token presence (no connect without a token); `userId` no longer used to build the connection
- [ ] Public hook signature kept stable (no caller/SCREEN changes required)
- [ ] Test updated: mocks session store token; asserts client called with the token; no `userId` in the connection
**Depends on:** CLI-1

---

## Dependency summary

| Task | Title | Depends on |
|------|-------|-----------|
| DOCS-1 | Decision log entry | none |
| DOCS-2 | Update websocket-feed flow | DOCS-1 |
| DOCS-3 | Align feed-routing flow | DOCS-2 |
| DOCS-4 | Note reuse in jwt-authentication flow | DOCS-2 |
| API-1 | Expose `TokenValidationApi` | DOCS-2 |
| SVC-1 | Implement `TokenValidationApi` | API-1 |
| CONTROLLER-1 | Accept subprotocol in WS config | DOCS-2 |
| SVC-2 | Authenticate handshake in handler | API-1, CONTROLLER-1 |
| CONTROLLER-2 | Refresh SecurityConfig comment | SVC-2 |
| CLI-1 | Send JWT via subprotocol | DOCS-2 |
| STATE-1 | Supply token from session store | CLI-1 |
