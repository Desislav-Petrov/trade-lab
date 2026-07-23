# Tasks: Authenticate with External Identity Provider

**Use case:** `domain/usecases/oidc-authentication`  
**Flows:** `domain/flows/oidc-login`, `domain/flows/jwt-authentication`  
**Models:** `domain/model/user`, `domain/model/external-identity-provider`, `domain/model/session`  

---

## Prerequisites — before any code is written

The following must be in place in your environment before implementing any task in this file:

| What | Where to get it | How it is consumed |
|---|---|---|
| Google OAuth2 Client ID | Google Cloud Console → APIs & Services → Credentials → Create OAuth 2.0 Client ID (Web application) | `GOOGLE_CLIENT_ID` environment variable |
| Google OAuth2 Client Secret | Same credential as above | `GOOGLE_CLIENT_SECRET` environment variable |
| HMAC JWT secret | Any random string ≥ 32 characters | `JWT_SECRET` environment variable (optional in dev — a default is wired in `application.yml`) |

When creating the Google credential, set the **Authorized redirect URI** to:  
`http://localhost:8080/login/oauth2/code/google`

---

## BUILD — Build

### BUILD-1 — Add Spring Security, OAuth2 client, and jjwt dependencies

**Layer:** Build  
**Domain:** global (`build.gradle.kts`)  
**Use case:** oidc-authentication  
**Implements:** prerequisite for SVC-1, CONTROLLER-1, CONTROLLER-2, CONTROLLER-3  
**Inputs:**
- Existing `build.gradle.kts`

**Outputs:**
- `spring-boot-starter-security` added to `dependencies` (version managed by Spring Boot BOM — no explicit version)
- `spring-boot-starter-oauth2-client` added to `dependencies` (version managed by Spring Boot BOM — no explicit version)
- `io.jsonwebtoken:jjwt-api:0.12.6` added to `dependencies` (explicit version — not in Spring Boot BOM)
- `io.jsonwebtoken:jjwt-impl:0.12.6` added as `runtimeOnly`
- `io.jsonwebtoken:jjwt-jackson:0.12.6` added as `runtimeOnly`

**Acceptance criteria:**
- [ ] `./gradlew build` completes without errors after the additions.
- [ ] All three jjwt artefacts (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) are on the same version.
- [ ] `spring-boot-starter-security` and `spring-boot-starter-oauth2-client` carry no explicit version — they resolve through the Spring Boot BOM.
- [ ] Existing tests still pass after the dependency additions (Spring Security auto-configuration may lock down all endpoints by default — `CONFIG-1` and `CONTROLLER-3` will address this; if existing tests break only because of the security auto-config, note it and proceed).

**Depends on:** none

---

### CONFIG-1 — Configure application.yml for OAuth2 provider, JWT, CORS, and frontend origin

**Layer:** Build  
**Domain:** global (`src/main/resources/application.yml`)  
**Use case:** oidc-authentication  
**Implements:** oidc-login — step 3 (Google provider registration); jwt-authentication — step 4 (HMAC secret); CONTROLLER-3 (CORS + frontend origin)  
**Inputs:**
- Existing `src/main/resources/application.yml`
- Environment variables: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `JWT_SECRET`, `FRONTEND_ORIGIN`

**Outputs:**
- `application.yml` extended with the following block (appended — do not overwrite existing config):

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid,email,profile
        provider:
          google:
            issuer-uri: https://accounts.google.com

app:
  jwt:
    secret: ${JWT_SECRET:dev-only-secret-change-me-in-production-32c}
  frontend:
    origin: ${FRONTEND_ORIGIN:http://localhost:5173}
  cors:
    allowed-origin: ${FRONTEND_ORIGIN:http://localhost:5173}
```

- `README.md` updated with a "Local setup" section listing the three required environment variables (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `JWT_SECRET`) and a link to the Google Cloud Console credential creation flow.

**Acceptance criteria:**
- [ ] Application starts without error when `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are set as environment variables.
- [ ] Application **fails to start** when `GOOGLE_CLIENT_ID` or `GOOGLE_CLIENT_SECRET` are absent — they carry no fallback default.
- [ ] Application starts with the dev default JWT secret when `JWT_SECRET` is not set.
- [ ] `app.jwt.secret` fallback value is exactly 32+ characters (minimum for HMAC-SHA256).
- [ ] `README.md` lists all three required env vars with a brief description and where to obtain them.

**Depends on:** BUILD-1

---

## DB — Database

### DB-1 — Make User.address nullable

**Layer:** Database  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — step 9b; user-registration — step 8  
**Inputs:**
- Existing `User` entity class in `user.model`

**Outputs:**
- Updated `User` entity with `address: String?` (nullable column)

**Acceptance criteria:**
- [ ] `address` field is typed `String?` on the `User` entity.
- [ ] The `@Column` mapping for `address` has `nullable = true`.
- [ ] All existing service/repository code that writes or reads `address` compiles without change (no forced non-null dereference).
- [ ] Existing unit tests for `UserService.registerUser` still pass.
- [ ] A `User` record can be persisted with `address = null` without a constraint violation.

**Depends on:** none

---

### DB-2 — Create ExternalIdentityProvider entity and ProviderType enum

**Layer:** Database  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — steps 7, 8a, 9a, 9b  
**Inputs:**
- `domain/model/external-identity-provider.md`

**Outputs:**
- `ProviderType` enum in `user.model` with value `GOOGLE`
- `ExternalIdentityProvider` entity class in `user.model` with fields: `id: UUID`, `userId: UUID`, `providerType: ProviderType`, `subId: String`, `email: String`, `lastAccessedAt: Instant`
- Unique constraint on `(providerType, subId)`
- Unique constraint on `(userId, providerType)`

**Acceptance criteria:**
- [ ] `ProviderType` enum is annotated `@Enumerated(EnumType.STRING)` on the entity field.
- [ ] Entity follows the `Persistable<UUID>` pattern with `@Transient _isNew: Boolean = true` (per `standards/backend.md`).
- [ ] `equals` and `hashCode` are implemented on `id` only.
- [ ] `(providerType, subId)` unique constraint is declared via `@Table(uniqueConstraints = [...])`.
- [ ] `(userId, providerType)` unique constraint is declared via `@Table(uniqueConstraints = [...])`.
- [ ] `subId` and `providerType` columns are marked `updatable = false`.
- [ ] A unit test verifies that persisting two rows with the same `(providerType, subId)` throws a constraint violation.

**Depends on:** none

---

## REPO — Repository

### REPO-1 — Create ExternalIdentityProviderRepository

**Layer:** Repository  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — steps 7, 8a  
**Inputs:**
- `ExternalIdentityProvider` entity (DB-2)

**Outputs:**
- `ExternalIdentityProviderRepository` interface in `user.repository` extending `JpaRepository<ExternalIdentityProvider, UUID>`
- Method: `findByProviderTypeAndSubId(providerType: ProviderType, subId: String): Optional<ExternalIdentityProvider>`
- Method: `findByUserIdAndProviderType(userId: UUID, providerType: ProviderType): Optional<ExternalIdentityProvider>`

**Acceptance criteria:**
- [ ] `findByProviderTypeAndSubId` returns the correct row when a matching record exists.
- [ ] `findByProviderTypeAndSubId` returns `Optional.empty()` when no match exists.
- [ ] `findByUserIdAndProviderType` returns the correct row when a matching record exists.
- [ ] Both methods are covered by a `@SpringBootTest` + `@Transactional` repository test against H2.

**Depends on:** DB-2

---

### REPO-2 — Verify UserRepository.findByEmail

**Layer:** Repository  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — step 8b  
**Inputs:**
- Existing `UserRepository` in `user.repository`

**Outputs:**
- `findByEmail(email: String): Optional<User>` on `UserRepository` (add if not already present; leave unchanged if it exists)

**Acceptance criteria:**
- [ ] `UserRepository` exposes `findByEmail(email: String): Optional<User>`.
- [ ] The method returns the correct `User` when the email matches.
- [ ] The method returns `Optional.empty()` when no user has that email.
- [ ] If the method already existed, its existing tests still pass unchanged.

**Depends on:** DB-1

---

## EXCEPTION — Exception

### EXCEPTION-1 — Create OidcAuthenticationException and InvalidTokenException

**Layer:** Exception  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — error cases; jwt-authentication — all error cases  
**Inputs:** none  
**Outputs:**
- `OidcAuthenticationException(message: String, cause: Throwable? = null)` in `user.exception` — thrown by `OidcAuthService` when the find-or-register sub-flow fails unexpectedly.
- `InvalidTokenException(message: String)` in `user.exception` — thrown by `JwtService` when a token is malformed, has an invalid signature, is expired, or has an issuer mismatch.

**Acceptance criteria:**
- [ ] Both classes extend `RuntimeException`.
- [ ] `GlobalExceptionHandler` maps `InvalidTokenException` to HTTP 401 with the standard error body shape.
- [ ] `GlobalExceptionHandler` maps `OidcAuthenticationException` to HTTP 500 with the standard error body shape.
- [ ] Unit tests on `GlobalExceptionHandler` verify the correct HTTP status is returned for each exception type.

**Depends on:** none

---

## SVC — Service

### SVC-1 — Create JwtService

**Layer:** Service  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — step 10; jwt-authentication — steps 4, 5  
**Inputs:**
- `app.jwt.secret` from `application.yml` (injected via `@Value`)
- For `issueToken`: `userId: UUID` → returns `String` (signed JWT)
- For `validateAndExtractUserId`: `token: String` → returns `UUID`

**Outputs:**
- `JwtService` class in `user.service`
- `issueToken(userId: UUID): String` — issues a JWT with `{ sub: userId.toString(), iat: now, exp: now+86400s, iss: "trade-platform" }` signed with HMAC-SHA256
- `validateAndExtractUserId(token: String): UUID` — parses and validates the JWT using jjwt; performs three checks: (1) HMAC-SHA256 signature against `app.jwt.secret`, (2) `exp` not in the past, (3) `iss` equals `"trade-platform"`; throws `InvalidTokenException` on any failure; returns the `sub` claim parsed as `UUID`. No database lookup is performed — validation is entirely cryptographic.

**Acceptance criteria:**
- [ ] `issueToken` produces a valid JWT parseable by the jjwt library.
- [ ] Issued token contains `sub` (userId as string), `iat`, `exp` (iat + 86400), `iss` = `"trade-platform"`.
- [ ] `validateAndExtractUserId` returns the correct UUID for a valid token.
- [ ] `validateAndExtractUserId` throws `InvalidTokenException` for an expired token.
- [ ] `validateAndExtractUserId` throws `InvalidTokenException` for a tampered signature.
- [ ] `validateAndExtractUserId` throws `InvalidTokenException` when `iss` ≠ `"trade-platform"`.
- [ ] `validateAndExtractUserId` throws `InvalidTokenException` for a malformed (non-JWT) string.
- [ ] No repository or database call is made inside `JwtService`.
- [ ] All cases covered by unit tests using KoTest + mockito-kotlin.

**Depends on:** BUILD-1, EXCEPTION-1

---

### SVC-2 — Create OidcAuthService

**Layer:** Service  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — steps 7, 8a, 8b, 9a, 9b, 10  
**Inputs:**
- `providerType: ProviderType`
- `subId: String`
- `email: String`
- `firstName: String`
- `lastName: String`

**Outputs:**
- `OidcAuthService` class in `user.service`
- `handleCallback(providerType, subId, email, firstName, lastName): String` — returns signed internal JWT string

**Acceptance criteria:**
- [ ] When `ExternalIdentityProvider` found by `(providerType, subId)`: loads the linked `User`; updates `lastAccessedAt`; issues JWT; returns token. No new User is created.
- [ ] When `ExternalIdentityProvider` not found and `User` found by email: creates `ExternalIdentityProvider` row linked to existing user; issues JWT; returns token. No new User is created.
- [ ] When `ExternalIdentityProvider` not found and `User` not found by email: creates `User` (address=null), `UserSettings` (feedType=SYNTHETIC), and `ExternalIdentityProvider` in a single `@Transactional` call; emits `UserRegistered`; issues JWT; returns token.
- [ ] All three paths are covered by unit tests (repository mocked).
- [ ] `UserRegistered` is published only in the auto-register path.
- [ ] The method is annotated `@Transactional`.
- [ ] Any unexpected exception is wrapped in `OidcAuthenticationException` before propagating.

**Depends on:** DB-1, DB-2, REPO-1, REPO-2, SVC-1, EXCEPTION-1

---

### SVC-3 — Make UserService.registerUser address optional

**Layer:** Service  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** user-registration — step 8  
**Inputs:**
- Existing `UserService.registerUser` signature

**Outputs:**
- Updated `registerUser` signature: `address` parameter becomes `String?` (nullable, defaults to `null`)
- `User` entity persisted with `address = null` when the parameter is absent

**Acceptance criteria:**
- [ ] `registerUser` compiles and passes existing tests when called with a non-null `address`.
- [ ] `registerUser` persists `address = null` when called without an address.
- [ ] The OpenAPI-generated `RegisterUserRequest` DTO does not mark `address` as required (addressed in API-CONTRACT-1; for this task, ensure the service layer does not enforce non-null).
- [ ] Existing `UserService` unit tests pass unchanged.
- [ ] A new unit test covers the case where `address` is `null`.

**Depends on:** DB-1

---

## CONTROLLER — Controller

### CONTROLLER-1 — Create OidcAuthenticationSuccessHandler

**Layer:** Controller  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — steps 7–11  
**Inputs:**
- `OidcUser` principal from Spring Security's authenticated context (contains `sub`, `email`, `given_name`, `family_name`)
- `OidcAuthService` (injected)
- `app.frontend.origin` from `application.yml` — the frontend base URL for the redirect

**Outputs:**
- `OidcAuthenticationSuccessHandler` class in `user.controller`, implementing `AuthenticationSuccessHandler`
- On success: redirects to `{frontend-origin}/auth/callback?token={jwt}`
- Registered as the success handler on the Spring Security OAuth2 login configuration

**Acceptance criteria:**
- [ ] Handler extracts `sub`, `email`, `given_name`, `family_name` from `OidcUser`.
- [ ] Handler delegates to `OidcAuthService.handleCallback` and receives the JWT string.
- [ ] Handler redirects to `{app.frontend.origin}/auth/callback?token={jwt}` with HTTP 302.
- [ ] If `OidcAuthService` throws `OidcAuthenticationException`, handler redirects to `{app.frontend.origin}/login?error=server_error`.
- [ ] Unit test mocks `OidcAuthService`; asserts correct redirect URL for the happy path and the error path.

**Depends on:** SVC-2, EXCEPTION-1

---

### CONTROLLER-2 — Create JwtAuthenticationFilter

**Layer:** Controller  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** jwt-authentication — steps 2–6  
**Inputs:**
- HTTP request `Authorization` header
- `JwtService` (injected)

**Outputs:**
- `JwtAuthenticationFilter` class in `user.controller`, extending `OncePerRequestFilter`
- Sets `UsernamePasswordAuthenticationToken` in `SecurityContextHolder` for valid tokens
- Writes HTTP 401 with the standard error body for invalid tokens

**Acceptance criteria:**
- [ ] Filter extracts the token from `Authorization: Bearer {token}` correctly.
- [ ] Filter calls `JwtService.validateAndExtractUserId` and sets the result as the authentication principal.
- [ ] If no `Authorization` header is present, the filter does not set authentication and calls `filterChain.doFilter` — Spring Security's access rules handle the 401.
- [ ] If the token is invalid (any `InvalidTokenException`), the filter writes HTTP 401 with the standard error body and does not call `filterChain.doFilter`.
- [ ] Unit tests cover: valid token sets SecurityContext; missing header passes through; invalid token returns 401.

**Depends on:** BUILD-1, SVC-1, EXCEPTION-1

---

### CONTROLLER-3 — Create SecurityConfig

**Layer:** Controller  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — step 3; jwt-authentication (cross-cutting)  
**Inputs:**
- `OidcAuthenticationSuccessHandler` (injected)
- `JwtAuthenticationFilter` (injected)
- `app.cors.allowed-origin` from `application.yml`

**Outputs:**
- `SecurityConfig` class in `config/` (global), annotated `@Configuration @EnableWebSecurity`
- Spring Security filter chain with:
  - CSRF disabled
  - CORS configured: allows `app.cors.allowed-origin`, methods `GET POST PUT PATCH DELETE OPTIONS`, headers including `Authorization`
  - OAuth2 login enabled with `OidcAuthenticationSuccessHandler` as the success handler and a failure handler that redirects to `{frontend-origin}/login?error=oidc_failed`
  - `JwtAuthenticationFilter` added before `UsernamePasswordAuthenticationFilter`
  - Public (permit all): `POST /api/v1/users/register`, `GET /api/v1/users`, `/oauth2/authorization/**`, `/login/oauth2/code/**`
  - All other requests: `authenticated()`

**Acceptance criteria:**
- [ ] `POST /api/v1/users/register` returns a non-401 response without a Bearer token.
- [ ] `GET /api/v1/users` returns a non-401 response without a Bearer token.
- [ ] Any other endpoint returns HTTP 401 when called without a Bearer token.
- [ ] Any other endpoint returns a 2xx response when called with a valid Bearer token.
- [ ] CORS headers include `Access-Control-Allow-Origin` matching the configured frontend origin.
- [ ] `Authorization` is in the allowed request headers.
- [ ] Integration test (H2, full context) verifies the public/protected split.

**Depends on:** CONFIG-1, CONTROLLER-1, CONTROLLER-2

---

## API-CONTRACT — OpenAPI Contract

### API-CONTRACT-1 — Add BearerAuth security scheme to all domain OpenAPI contracts

**Layer:** OpenAPI Contract  
**Domain:** user, ledger, marketdata, stocktrading, portfolio  
**Use case:** oidc-authentication  
**Implements:** jwt-authentication (cross-cutting security contract)  
**Inputs:**
- All existing domain OpenAPI YAML files in `services/contract/`
- List of public operations: `POST /api/v1/users/register`, `GET /api/v1/users`

**Outputs:**
- Each domain YAML updated with:
  - `components.securitySchemes.BearerAuth` (type: `http`, scheme: `bearer`, bearerFormat: `JWT`)
  - Global `security: [BearerAuth: []]` applied to all operations, except `POST /api/v1/users/register` and `GET /api/v1/users` which receive `security: []` (override to public)
- The `address` field in the `RegisterUserRequest` schema (user domain YAML) updated to be optional (not in `required` list)

**Acceptance criteria:**
- [ ] Every domain YAML contains `components.securitySchemes.BearerAuth` with type `http`, scheme `bearer`, bearerFormat `JWT`.
- [ ] Every operation except the two public ones has `security: [{BearerAuth: []}]` (either via global default or per-operation).
- [ ] `POST /api/v1/users/register` and `GET /api/v1/users` have `security: []`.
- [ ] `address` is absent from the `required` list in `RegisterUserRequest`.
- [ ] `./gradlew openApiGenerate` completes without errors after the changes.
- [ ] Generated `RegisterUserRequest` has `address: String?`.

**Depends on:** CONTROLLER-3, SVC-3

---

## CLI — API Client

### CLI-1 — Add Bearer token request interceptor to shared Axios instance

**Layer:** API Client  
**Domain:** user (shared)  
**Use case:** oidc-authentication  
**Implements:** jwt-authentication — step 1  
**Inputs:**
- Existing shared Axios instance in `shared/api/`
- `accessToken` from `localStorage` (key: `session`)

**Outputs:**
- Updated Axios instance with a request interceptor that reads `accessToken` from the session in `localStorage` and adds `Authorization: Bearer {token}` to every request when the token is present

**Acceptance criteria:**
- [ ] Every outgoing Axios request includes `Authorization: Bearer {token}` when a valid session exists in `localStorage`.
- [ ] Requests made before login (no session in `localStorage`) are sent without an `Authorization` header.
- [ ] The interceptor does not throw if `localStorage` is empty or the session is malformed.
- [ ] Unit test mocks `localStorage` and asserts the header is set correctly for both cases.

**Depends on:** none

---

### CLI-2 — Add oidcApi.ts with redirectToGoogleLogin

**Layer:** API Client  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — step 2  
**Inputs:** none  
**Outputs:**
- `src/domains/user/api/oidcApi.ts`
- `redirectToGoogleLogin(): void` — sets `window.location.href` to `/oauth2/authorization/google`

**Acceptance criteria:**
- [ ] `redirectToGoogleLogin` navigates to `/oauth2/authorization/google`.
- [ ] The function is exported and typed.
- [ ] Unit test mocks `window.location` and asserts the correct URL is set.

**Depends on:** none

---

## STATE — State

### STATE-1 — Update session Zustand store

**Layer:** State  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — steps 12, 13; oidc-authentication use case — JWT expired scenario  
**Inputs:**
- Existing session Zustand store in `src/domains/user/hooks/`
- JWT string received from `/auth/callback?token=`
- Full user profile from `GET /api/v1/users/{userId}`

**Outputs:**
- Updated session store with:
  - `accessToken: string` added to the session state shape
  - `address: string | null` (previously required string, now nullable)
  - `establishSession(profile: UserProfile, accessToken: string): void` — stores profile + token + `loggedInAt`, persists full session to `localStorage`
  - `restoreSession(): void` — called on app init; reads `localStorage`; decodes JWT `exp` claim client-side; if expired or absent, does not restore; if valid, populates store
  - `clearSession(): void` — clears Zustand store and removes session from `localStorage`
- TypeScript types updated in `src/domains/user/types/`

**Acceptance criteria:**
- [ ] `establishSession` persists the full session (including `accessToken`) to `localStorage`.
- [ ] `restoreSession` populates the store from `localStorage` when the token is not expired.
- [ ] `restoreSession` does not populate the store and returns without error when `localStorage` is empty.
- [ ] `restoreSession` does not populate the store when the stored token's `exp` is in the past.
- [ ] `clearSession` removes all session data from both the Zustand store and `localStorage`.
- [ ] All actions covered by unit tests using `renderHook` and mocked `localStorage`.

**Depends on:** none

---

## COMP — Component

### COMP-1 — LoginWithGoogleButton component

**Layer:** Component  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — steps 1, 2  
**Inputs:**
- Props: `LoginWithGoogleButtonProps { onClick: () => void }`

**Outputs:**
- `src/domains/user/components/LoginWithGoogleButton.tsx`
- A styled button labelled "Login with Google" that calls `props.onClick` when clicked

**Acceptance criteria:**
- [ ] Renders a button with accessible text "Login with Google".
- [ ] Calls `props.onClick` exactly once when clicked.
- [ ] Does not make any API calls directly.
- [ ] Has an explicit `LoginWithGoogleButtonProps` TypeScript interface.
- [ ] Unit test: renders correctly; click calls `onClick`.

**Depends on:** none

---

### COMP-2 — AuthCallbackHandler component

**Layer:** Component  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — steps 12, 13  
**Inputs:**
- `token` query parameter from the current URL
- `establishSession` action from the session store (STATE-1)
- `GET /api/v1/users/{userId}` via the user API client

**Outputs:**
- `src/domains/user/components/AuthCallbackHandler.tsx`
- On mount: reads `token` from URL, decodes userId from `sub` claim, fetches user profile, calls `establishSession`
- While loading: renders a loading indicator
- On error: renders an error message ("Authentication failed. Please try again.")
- On success: signals the parent page to redirect to `/trade`

**Acceptance criteria:**
- [ ] Reads `token` from `?token=` query param on mount.
- [ ] Calls `establishSession` with the fetched profile and the token on success.
- [ ] Renders a loading indicator while the profile fetch is in flight.
- [ ] Renders an error message if `token` is absent from the URL.
- [ ] Renders an error message if the profile fetch fails.
- [ ] Does not call `establishSession` on error.
- [ ] Unit tests cover: success path, missing token, fetch failure.

**Depends on:** STATE-1, CLI-1

---

## SCREEN — Screen

### SCREEN-1 — Update LoginPage with Google login option

**Layer:** Screen  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — step 1; oidc-authentication failure scenarios  
**Inputs:**
- Existing `LoginPage` in `src/domains/user/pages/`
- `LoginWithGoogleButton` component (COMP-1)
- `redirectToGoogleLogin` from `oidcApi.ts` (CLI-2)
- `error` query parameter from the URL (`oidc_failed`, `server_error`)

**Outputs:**
- Updated `LoginPage` with:
  - A "Login with Google" section above or alongside the existing email-select form, rendering `LoginWithGoogleButton`
  - An error banner displayed when `?error=oidc_failed` is in the URL: "Authentication failed. Please try again."
  - An error banner displayed when `?error=server_error` is in the URL: "Something went wrong. Please try again."

**Acceptance criteria:**
- [ ] `LoginWithGoogleButton` is rendered on the login page.
- [ ] Clicking the button calls `redirectToGoogleLogin()`.
- [ ] Error banner is shown for `?error=oidc_failed`.
- [ ] Error banner is shown for `?error=server_error`.
- [ ] No error banner is shown when no `error` query param is present.
- [ ] Existing email-select form remains functional and unmodified.
- [ ] Unit tests cover: default render, oidc_failed error, server_error error.

**Depends on:** COMP-1, CLI-2

---

### SCREEN-2 — Add AuthCallbackPage at /auth/callback

**Layer:** Screen  
**Domain:** user  
**Use case:** oidc-authentication  
**Implements:** oidc-login — steps 12–14  
**Inputs:**
- `AuthCallbackHandler` component (COMP-2)
- React Router — current URL and `useNavigate`

**Outputs:**
- `src/domains/user/pages/AuthCallbackPage.tsx` — mounts `AuthCallbackHandler`; on successful session establishment navigates to `/trade`; on error shows error message
- `src/app/router.tsx` updated: new route `path="/auth/callback"` → `AuthCallbackPage`

**Acceptance criteria:**
- [ ] Route `/auth/callback` renders `AuthCallbackPage`.
- [ ] On successful session establishment, page navigates to `/trade`.
- [ ] On error (missing token or failed fetch), page displays the error from `AuthCallbackHandler` without navigating.
- [ ] Page does not contain any business logic — all logic is in `AuthCallbackHandler`.
- [ ] Unit tests cover: success → redirect to `/trade`; error → error message displayed.

**Depends on:** COMP-2, STATE-1

---

## Dependency Summary

| Task ID | Title | Depends on |
|---------|-------|------------|
| BUILD-1 | Add Spring Security, OAuth2 client, and jjwt dependencies | none |
| CONFIG-1 | Configure application.yml for OAuth2, JWT, CORS, frontend origin | BUILD-1 |
| DB-1 | Make User.address nullable | none |
| DB-2 | Create ExternalIdentityProvider entity + ProviderType enum | none |
| REPO-1 | Create ExternalIdentityProviderRepository | DB-2 |
| REPO-2 | Verify UserRepository.findByEmail | DB-1 |
| EXCEPTION-1 | Create OidcAuthenticationException and InvalidTokenException | none |
| SVC-1 | Create JwtService | BUILD-1, EXCEPTION-1 |
| SVC-2 | Create OidcAuthService | DB-1, DB-2, REPO-1, REPO-2, SVC-1, EXCEPTION-1 |
| SVC-3 | Make UserService.registerUser address optional | DB-1 |
| CONTROLLER-1 | Create OidcAuthenticationSuccessHandler | SVC-2, EXCEPTION-1 |
| CONTROLLER-2 | Create JwtAuthenticationFilter | BUILD-1, SVC-1, EXCEPTION-1 |
| CONTROLLER-3 | Create SecurityConfig | CONFIG-1, CONTROLLER-1, CONTROLLER-2 |
| API-CONTRACT-1 | Add BearerAuth security scheme to all domain OpenAPI contracts | CONTROLLER-3, SVC-3 |
| CLI-1 | Add Bearer token interceptor to shared Axios instance | none |
| CLI-2 | Add oidcApi.ts with redirectToGoogleLogin | none |
| STATE-1 | Update session Zustand store | none |
| COMP-1 | LoginWithGoogleButton component | none |
| COMP-2 | AuthCallbackHandler component | STATE-1, CLI-1 |
| SCREEN-1 | Update LoginPage with Google login option | COMP-1, CLI-2 |
| SCREEN-2 | Add AuthCallbackPage at /auth/callback | COMP-2, STATE-1 |
