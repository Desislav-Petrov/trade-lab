# [CONTROLLER-1] — Update UserApiDelegateImpl.loginUser to return 302 redirect

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
