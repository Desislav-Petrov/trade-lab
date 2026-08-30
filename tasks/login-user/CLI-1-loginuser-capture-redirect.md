# [CLI-1] — Update loginUser in userApi.ts to capture the 302 redirect URL

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
