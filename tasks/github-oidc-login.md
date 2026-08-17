# Tasks: GitHub OAuth2 Login

**Use case:** `github-oidc-login`  
**Flows:** `domain/flows/github-oidc-login`, `domain/flows/oidc-login` (updated)  
**Decision log:** `decisions/2026-08-17-github-oauth2-provider.md`

---

## DB Layer

### [DB-1] — Add GITHUB to ProviderType enum

**Layer:** Database  
**Domain:** user  
**Use case:** github-oidc-login  
**Implements:** github-oidc-login — step 9 (look up by `providerType=GITHUB`)  
**Inputs:** Existing `ProviderType` enum at `org.dpp.tradelab.user.model.ProviderType`  
**Outputs:** `ProviderType` enum with values `GOOGLE` and `GITHUB`  
**Acceptance criteria:**
- [ ] `ProviderType.GITHUB` is added as a second enum value alongside `GOOGLE`.
- [ ] No other changes are made to the enum file.
- [ ] The file compiles cleanly with `./gradlew compileKotlin`.

**Depends on:** none

---

## SVC Layer

### [SVC-1] — Extend OidcAuthenticationSuccessHandler to support GitHub

**Layer:** Service  
**Domain:** user  
**Use case:** github-oidc-login  
**Implements:** github-oidc-login — steps 7 (email validation) and 8 (name splitting); oidc-login — step 1 (provider detection in handler)  
**Inputs:**
- `authentication: Authentication` (Spring Security `OAuth2AuthenticationToken`)
- `OAuth2AuthenticationToken.authorizedClientRegistrationId: String` — `"google"` or `"github"`
- For Google path: `OidcUser` principal with `subject`, `email`, `givenName`, `familyName`
- For GitHub path: `OAuth2User` principal with attributes `id` (numeric), `email` (nullable), `name` (nullable)

**Outputs:**
- For both paths: calls `oidcAuthService.handleCallback(providerType, subId, email, firstName, lastName)` with correctly derived values and redirects to `{frontendOrigin}/auth/callback?token={jwt}`
- GitHub no-email path: redirects to `{frontendOrigin}/login?error=github_no_email` without calling the service
- Failure (either path): redirects to `{frontendOrigin}/login?error=oidc_failed` (Google) or `?error=github_oidc_failed` (GitHub) or `?error=server_error`

**Acceptance criteria:**
- [ ] `OidcAuthenticationSuccessHandler` inspects `(authentication as OAuth2AuthenticationToken).authorizedClientRegistrationId` to branch between `"google"` and `"github"`.
- [ ] Google branch: extracts `subId = oidcUser.subject`, `email = oidcUser.email`, `firstName = oidcUser.givenName ?: "User"`, `lastName = oidcUser.familyName ?: ""`. Behaviour is identical to the pre-existing implementation.
- [ ] GitHub branch: extracts `subId = oauth2User.attributes["id"].toString()`, `email = oauth2User.attributes["email"] as String?`.
- [ ] GitHub branch: if `email` is null or blank, redirects to `{frontendOrigin}/login?error=github_no_email` and returns without calling `oidcAuthService`.
- [ ] GitHub branch: splits `name = oauth2User.attributes["name"] as String?` on the first space. If a space is present: `firstName` = text before, `lastName` = text after. If no space (or null/blank): `firstName` = full value or `"User"`, `lastName` = `""`.
- [ ] GitHub branch: calls `oidcAuthService.handleCallback(ProviderType.GITHUB, subId, email, firstName, lastName)`.
- [ ] `SecurityConfig` failure handler for the GitHub path redirects to `?error=github_oidc_failed`; Google path continues to use `?error=oidc_failed`.
- [ ] Any `OidcAuthenticationException` redirects to `{frontendOrigin}/login?error=server_error`.
- [ ] Any other unexpected exception redirects to `{frontendOrigin}/login?error=server_error`.
- [ ] Unit tests cover: Google happy path (unchanged), GitHub happy path, GitHub null email → `github_no_email` redirect, GitHub blank email → `github_no_email` redirect, GitHub single-token name, GitHub two-token name, GitHub multi-token name (split on first space only), GitHub null name, `OidcAuthenticationException` → `server_error`, unexpected exception → `server_error`.

**Depends on:** DB-1

---

## CONFIG Layer

### [CONFIG-1] — Add GitHub OAuth2 client registration to application.yml

**Layer:** Service  
**Domain:** user  
**Use case:** github-oidc-login  
**Implements:** github-oidc-login — preconditions (backend configured with GitHub client ID and secret)  
**Inputs:** `services/backend/src/main/resources/application.yml`  
**Outputs:** Updated `application.yml` with GitHub OAuth2 registration block added under `spring.security.oauth2.client.registration`  
**Acceptance criteria:**
- [ ] The following block is added under `spring.security.oauth2.client.registration`:
  ```yaml
  github:
    client-id: ${GITHUB_CLIENT_ID:not-configured}
    client-secret: ${GITHUB_CLIENT_SECRET:not-configured}
    scope: read:user,user:email
  ```
- [ ] No existing configuration is modified or removed.
- [ ] `./gradlew bootRun` starts without errors when `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` are not set (falls back to `not-configured`).
- [ ] The `SecurityConfig` does not require any changes — Spring Security auto-registers the `github` provider by its built-in registration name.

**Depends on:** none

---

## COMP Layer

### [COMP-1] — Create LoginWithGithubButton component

**Layer:** Component  
**Domain:** user  
**Use case:** github-oidc-login  
**Implements:** github-oidc-login — step 1 (render "Login with GitHub" button)  
**Inputs:**
- `onClick: () => void` prop

**Outputs:**
- `LoginWithGithubButton` component at `services/front-end/src/domains/user/components/LoginWithGithubButton.tsx`
- Test file at `services/front-end/src/domains/user/components/LoginWithGithubButton.test.tsx`

**Acceptance criteria:**
- [ ] Component is named `LoginWithGithubButton` with a `LoginWithGithubButtonProps` interface containing `onClick: () => void`.
- [ ] Renders a `<button>` with label "Login with GitHub" and the GitHub mark SVG icon (black Octocat or GitHub logo mark).
- [ ] Clicking the button calls `onClick`.
- [ ] Styling is consistent with `LoginWithGoogleButton` — same Tailwind classes, same width, same layout pattern.
- [ ] Test covers: renders with correct label, clicking calls `onClick`.
- [ ] No API calls, no state store access inside the component.

**Depends on:** none

---

## CLI Layer

### [CLI-1] — Add redirectToGithubLogin function to oidcApi

**Layer:** API Client  
**Domain:** user  
**Use case:** github-oidc-login  
**Implements:** github-oidc-login — step 2 (browser redirect to `/oauth2/authorization/github`)  
**Inputs:** Existing `services/front-end/src/domains/user/api/oidcApi.ts`  
**Outputs:** `redirectToGithubLogin` function added to `oidcApi.ts`; test coverage added to `oidcApi.test.ts`  
**Acceptance criteria:**
- [ ] `redirectToGithubLogin()` sets `window.location.href = '/oauth2/authorization/github'`.
- [ ] The function is exported from `oidcApi.ts` alongside the existing `redirectToGoogleLogin`.
- [ ] Test covers: calling `redirectToGithubLogin` sets `window.location.href` to `/oauth2/authorization/github`.
- [ ] No new Axios instance is created; no HTTP call is made (this is a browser redirect).

**Depends on:** none

---

## STATE Layer

### [STATE-1] — Extend OIDC error message map with GitHub-specific error codes

**Layer:** State  
**Domain:** user  
**Use case:** github-oidc-login  
**Implements:** github-oidc-login — error cases (GitHub auth denied, GitHub no public email)  
**Inputs:** Existing `OIDC_ERROR_MESSAGES` map in `services/front-end/src/domains/user/pages/LoginPage.tsx`  
**Outputs:** Updated `OIDC_ERROR_MESSAGES` map; updated `LoginPage.test.tsx` test coverage  

> **Note:** This is a state/data change isolated to the constant map. It lives in `LoginPage.tsx` today — the update is scoped to the map only, not the page layout.

**Acceptance criteria:**
- [ ] `OIDC_ERROR_MESSAGES` is updated to:
  ```ts
  const OIDC_ERROR_MESSAGES: Record<string, string> = {
    oidc_failed: 'Google authentication failed. Please try again.',
    github_oidc_failed: 'GitHub authentication failed. Please try again.',
    github_no_email: 'Your GitHub account has no public email. Please make your email public on GitHub and try again.',
    server_error: 'Something went wrong. Please try again.',
  }
  ```
- [ ] The existing `oidc_failed` message is updated from the generic form to "Google authentication failed. Please try again."
- [ ] Tests cover: `?error=oidc_failed` renders Google-specific message, `?error=github_oidc_failed` renders GitHub-specific message, `?error=github_no_email` renders the no-email message, `?error=server_error` renders the generic message.

**Depends on:** none

---

## SCREEN Layer

### [SCREEN-1] — Add "Login with GitHub" button to LoginPage

**Layer:** Screen  
**Domain:** user  
**Use case:** github-oidc-login  
**Implements:** github-oidc-login — step 1 (render login page with GitHub button)  
**Inputs:**
- `LoginWithGithubButton` component (from COMP-1)
- `redirectToGithubLogin` function (from CLI-1)
- Updated `OIDC_ERROR_MESSAGES` map (from STATE-1)

**Outputs:** Updated `LoginPage.tsx`; updated `LoginPage.test.tsx`  
**Acceptance criteria:**
- [ ] `LoginPage` imports `LoginWithGithubButton` from `../components/LoginWithGithubButton`.
- [ ] `LoginPage` imports `redirectToGithubLogin` from `../api/oidcApi`.
- [ ] A `handleGithubLogin` function calls `redirectToGithubLogin()`.
- [ ] `<LoginWithGithubButton onClick={handleGithubLogin} />` is rendered directly below `<LoginWithGoogleButton>`, before the "or continue with email" divider.
- [ ] The `github_no_email` error code renders the correct message (wired via the updated `OIDC_ERROR_MESSAGES` map from STATE-1).
- [ ] Tests cover: GitHub button renders, clicking GitHub button calls `redirectToGithubLogin`, `?error=github_no_email` displays the correct error message.
- [ ] No business logic is added to the page — all logic is in hooks and API functions.

**Depends on:** COMP-1, CLI-1, STATE-1

---

## Dependency Summary

| Task ID | Title | Depends on |
|---------|-------|------------|
| DB-1 | Add GITHUB to ProviderType enum | none |
| SVC-1 | Extend OidcAuthenticationSuccessHandler for GitHub | DB-1 |
| CONFIG-1 | Add GitHub OAuth2 client registration to application.yml | none |
| COMP-1 | Create LoginWithGithubButton component | none |
| CLI-1 | Add redirectToGithubLogin to oidcApi | none |
| STATE-1 | Extend OIDC error message map with GitHub error codes | none |
| SCREEN-1 | Add "Login with GitHub" button to LoginPage | COMP-1, CLI-1, STATE-1 |
