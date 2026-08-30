# [STATE-1] — Update useLoginUser hook to navigate to /auth/callback on success

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
