# Tasks: Login a User (JWT Issuance)

**Use case:** `domain/usecases/login-user`
**Flows:** `domain/flows/user-login`, `domain/flows/jwt-authentication`
**Models:** `domain/model/user`, `domain/model/session`
**Decision log:** `decisions/2026-08-30-email-login-jwt-issuance.md`

---

## Context

The email-select login flow previously returned a JSON body `{ userId, email }` and
the frontend fetched the user profile directly. This use case upgrades that flow so
the backend issues the same internal JWT as the OIDC flows and redirects to
`{frontend-origin}/auth/callback?token={jwt}`. The existing `AuthCallbackHandler`
component handles the rest — identical to the OAuth2 path.

`JwtService`, `AuthCallbackPage`, and `AuthCallbackHandler` already exist and are
**not** touched by these tasks. Only the login-specific path is changed.

---

## SVC Layer

### [SVC-1] — Extend UserService.loginUser to issue JWT and return it

**Layer:** Service
**Domain:** user
**Use case:** login-user
**Implements:** user-login — steps 4, 5, 6 (resolve user → issue JWT → emit UserLoggedIn)
**Inputs:**
- Existing `UserService.loginUser(email: String): User` in `user.service`
- `JwtService` (already exists in `user.service`) — inject via constructor
**Outputs:**
- `UserService.loginUser(email: String): String` — return type changes from `User` to `String` (the signed JWT)
- Method resolves user by email, validates `status == ACTIVE`, calls `JwtService.issueToken(user.id)`, publishes `UserLoggedInEvent`, returns the JWT string

**Acceptance criteria:**
- [ ] `loginUser` return type is `String` (the JWT).
- [ ] When user is found and active: calls `JwtService.issueToken(user.id)`, publishes `UserLoggedInEvent` with `userId`, `email`, `timestamp = Instant.now()`, and returns the JWT string.
- [ ] When email does not resolve to a user: throws `UserNotFoundException` — unchanged.
- [ ] When resolved user is not active: throws `UserNotActiveException` — unchanged.
- [ ] `JwtService` is injected via constructor — no field injection.
- [ ] Unit tests cover: happy path returns JWT string, user not found throws `UserNotFoundException`, user not active throws `UserNotActiveException`, `UserLoggedInEvent` is published only on the happy path.
- [ ] All existing `UserService` tests that do not touch `loginUser` continue to pass.

**Depends on:** none

---

## CONTROLLER Layer

### [CONTROLLER-1] — Update UserApiDelegateImpl.loginUser to return 302 redirect

**Layer:** Controller
**Domain:** user
**Use case:** login-user
**Implements:** user-login — step 7 (backend redirects browser to `{frontend-origin}/auth/callback?token={jwt}`)
**Inputs:**
- Updated `UserService.loginUser(email: String): String` from SVC-1
- `app.frontend.origin` from `application.yml` — inject via `@Value` into the delegate
- `LoginRequest` generated DTO (unchanged)
**Outputs:**
- `UserApiDelegateImpl.loginUser` returns `ResponseEntity<Void>` with HTTP 302 and `Location: {frontendOrigin}/auth/callback?token={jwt}`
- Response body is empty
- `POST /api/v1/users/login` verified to be in the `SecurityConfig` permit-all list (add if absent)

**Acceptance criteria:**
- [ ] `loginUser` calls `userService.loginUser(loginRequest.email)` which returns the JWT string.
- [ ] Returns `ResponseEntity.status(HttpStatus.FOUND).location(URI("{frontendOrigin}/auth/callback?token={jwt}")).build()`.
- [ ] HTTP response status is `302`.
- [ ] `Location` header is exactly `{app.frontend.origin}/auth/callback?token={jwt}`.
- [ ] Response body is empty.
- [ ] `POST /api/v1/users/login` is in the `SecurityConfig` permit-all list (verify; add if missing).
- [ ] MockMvc unit tests cover: happy path returns HTTP 302 with correct `Location` header; user not found returns HTTP 404; user not active returns HTTP 403.

**Depends on:** SVC-1

---

## API-CONTRACT Layer

### [API-CONTRACT-1] — Update user-openapi.yaml: loginUser returns 302 redirect

**Layer:** OpenAPI Contract
**Domain:** user
**Use case:** login-user
**Implements:** user-login — step 7 (contract reflects redirect, not JSON body)
**Inputs:**
- Existing `services/contract/user-openapi.yaml`
- `POST /users/login` operation (currently returns `200` + `LoginResponse` JSON body)
**Outputs:**
- `POST /users/login` updated:
  - `200` response removed
  - `302` response added: description `"Login successful — redirecting to frontend callback with JWT"`, no response body schema, `Location` response header documented
  - `security: []` override added to mark the operation as public (no Bearer required)
- `LoginResponse` schema removed from `components/schemas`
- `./gradlew openApiGenerate` completes without errors after the changes

**Acceptance criteria:**
- [ ] `POST /users/login` no longer has a `200` response.
- [ ] `POST /users/login` has a `302` response with the `Location` header documented.
- [ ] `POST /users/login` has `security: []` (public endpoint override).
- [ ] `LoginResponse` schema is removed from `components/schemas`.
- [ ] `./gradlew openApiGenerate` completes without errors.
- [ ] The generated `UsersApiDelegate` method signature for `loginUser` returns `ResponseEntity<Void>` — verify `UserApiDelegateImpl` compiles after the contract change.

**Depends on:** CONTROLLER-1

---

## CLI Layer

### [CLI-1] — Update loginUser in userApi.ts to capture the 302 redirect URL

**Layer:** API Client
**Domain:** user
**Use case:** login-user
**Implements:** user-login — step 7 (frontend captures the backend redirect URL containing the JWT)
**Inputs:**
- Existing `loginUser(request: LoginRequest): Promise<LoginResponse>` in `services/front-end/src/domains/user/api/userApi.ts`
- `LoginResponse` type in `user/types/user.ts`
**Outputs:**
- `loginUser` updated: Axios call configured with `maxRedirects: 0` and `validateStatus: (s) => s === 302`; returns the `Location` response header value (the full callback URL)
- Return type changes to `Promise<string>`
- `LoginResponse` interface removed from `user/types/user.ts`
- `LOGIN_USER_KEY` cache key constant removed from `userApi.ts`
- `userApi.test.ts` updated to cover the new return type and behaviour

**Acceptance criteria:**
- [ ] `loginUser` posts to `/v1/users/login` and returns the `Location` header string from the `302` response.
- [ ] Axios is configured to treat `302` as success for this call (`validateStatus: (s) => s === 302`) and not follow the redirect (`maxRedirects: 0`).
- [ ] Return type is `Promise<string>`.
- [ ] `LoginResponse` type is removed from `user/types/user.ts`.
- [ ] `LOGIN_USER_KEY` constant is removed from `userApi.ts`.
- [ ] Unit tests cover: successful `302` response returns the `Location` URL string; non-302 response throws an error.

**Depends on:** API-CONTRACT-1

---

## STATE Layer

### [STATE-1] — Update useLoginUser hook to navigate to /auth/callback on success

**Layer:** State
**Domain:** user
**Use case:** login-user
**Implements:** user-login — steps 7, 8, 9 (on success, navigate browser to `/auth/callback?token={jwt}`)
**Inputs:**
- Updated `loginUser` API function from CLI-1 — returns `Promise<string>` (the redirect URL)
- Existing `useLoginUser` hook in `services/front-end/src/domains/user/hooks/useLoginUser.ts`
**Outputs:**
- `useLoginUser` mutation `onSuccess` receives `redirectUrl: string`
- On success: calls `window.location.assign(redirectUrl)` — browser navigates to `/auth/callback?token={jwt}` where `AuthCallbackHandler` takes over (identical to the OIDC path)
- `UseLoginUserOptions.onSuccess` type updated to `(redirectUrl: string) => void`
- `LoginResponse` import removed from the hook file
- `useLoginUser.test.ts` updated

**Acceptance criteria:**
- [ ] `useLoginUser` mutation `onSuccess` receives a `string` (the redirect URL).
- [ ] On success: calls `window.location.assign(redirectUrl)`.
- [ ] `UseLoginUserOptions.onSuccess` is typed `(redirectUrl: string) => void`.
- [ ] `LoginResponse` import is removed.
- [ ] Unit tests cover: successful mutation triggers `window.location.assign` with the correct URL; error path does not call `window.location.assign`.

**Depends on:** CLI-1

---

## SCREEN Layer

### [SCREEN-1] — Simplify LoginPage and LoginForm — remove manual profile fetch

**Layer:** Screen
**Domain:** user
**Use case:** login-user
**Implements:** user-login — steps 7–10 (after login submit, browser navigates to `/auth/callback`; `AuthCallbackHandler` handles session setup and redirect to `/trade`)
**Inputs:**
- Updated `useLoginUser` hook from STATE-1 — success now drives `window.location.assign` internally
- Existing `LoginPage.tsx` in `services/front-end/src/domains/user/pages/`
- Existing `LoginForm.tsx` in `services/front-end/src/domains/user/components/`
**Outputs:**
- `LoginPage.tsx` updated:
  - `useFetchUserProfile` import and usage removed
  - `handleSuccess` function and `profileError` state removed
  - Profile-error `Alert` removed
  - `LoginForm` `onSuccess` prop removed (navigation is now handled inside the hook)
- `LoginForm.tsx` updated:
  - `onSuccess` prop and `LoginFormProps` interface removed (or prop removed from the interface) since `useLoginUser` handles navigation directly
- `LoginPage.test.tsx` and `LoginForm.test.tsx` updated to reflect removed profile-fetch logic

**Acceptance criteria:**
- [ ] `LoginPage` no longer imports or uses `useFetchUserProfile`.
- [ ] `LoginPage` no longer renders a `profileError` `Alert`.
- [ ] After a successful email-select login, the browser navigates to `/auth/callback?token={jwt}` — not to `/profile`.
- [ ] The OIDC buttons (`LoginWithGoogleButton`, `LoginWithGithubButton`) and OIDC error banner logic are unchanged.
- [ ] The email-select dropdown and submit button still render correctly.
- [ ] `LoginPage.test.tsx` covers: default render (email form + OIDC buttons present), successful login triggers `window.location.assign` to `/auth/callback`, OIDC error banners still render correctly.
- [ ] No business logic is added to the page.

**Depends on:** STATE-1

---

## Dependency Summary

| Task ID | Title | Depends on |
|---|---|---|
| SVC-1 | Extend UserService.loginUser to issue JWT and return it | none |
| CONTROLLER-1 | Update UserApiDelegateImpl.loginUser to return 302 redirect | SVC-1 |
| API-CONTRACT-1 | Update user-openapi.yaml: loginUser returns 302 | CONTROLLER-1 |
| CLI-1 | Update loginUser in userApi.ts to capture 302 redirect URL | API-CONTRACT-1 |
| STATE-1 | Update useLoginUser hook to navigate to /auth/callback | CLI-1 |
| SCREEN-1 | Simplify LoginPage and LoginForm — remove manual profile fetch | STATE-1 |
