# [SCREEN-1] — Simplify LoginPage and LoginForm — remove manual profile fetch

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
  - `onSuccess` prop and `LoginFormProps` interface removed since `useLoginUser` handles navigation directly
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
