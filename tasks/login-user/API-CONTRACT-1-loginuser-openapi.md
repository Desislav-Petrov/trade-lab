# [API-CONTRACT-1] — Update user-openapi.yaml: loginUser returns 302 redirect

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
