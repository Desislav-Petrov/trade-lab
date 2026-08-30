# [SVC-1] — Extend UserService.loginUser to issue JWT and return it

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
