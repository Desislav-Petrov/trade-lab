# GitHub OAuth2 Login

## Overview

Allows a guest to authenticate using their GitHub account and establish a session on the paper trading platform. Runs in parallel with the existing Google OIDC login path (`domain/flows/oidc-login`). On first use, a user profile is auto-created from the GitHub user-info claims. On subsequent uses, the existing profile is loaded. In both cases the backend issues an internal JWT which the frontend stores in `localStorage`.

This flow runs entirely via a backend-driven OAuth2 dance — the frontend never handles the GitHub token directly.

GitHub's OAuth2 implementation differs from Google OIDC: there is no ID token; the unique identifier is the numeric GitHub user ID; the user's name is a single field; and the user's email may be private (null). See `decisions/2026-08-17-github-oauth2-provider.md` for full rationale.

## Actors

- **Guest**: An unauthenticated visitor who chooses to log in via GitHub.
- **Guest Browser**: The React frontend rendering the login page and handling the post-callback redirect.
- **System**: The Spring Boot backend — Spring Security OAuth2 filter chain, `OidcAuthService`, `JwtService`.
- **GitHub**: The external identity provider.

## Preconditions

- The guest has a GitHub account with a public email address set.
- The backend is configured with a valid GitHub OAuth2 client ID and secret (`GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`).

## Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest Browser | Render login page | Displays both "Login with Google" and "Login with GitHub" buttons at `/login`. |
| 2 | Guest | Click "Login with GitHub" | Triggers a browser redirect to `/oauth2/authorization/github`. |
| 3 | System | Redirect to GitHub | Spring Security redirects the browser to GitHub's OAuth2 authorisation endpoint with the configured client ID, scopes (`read:user,user:email`), and redirect URI. |
| 4 | Guest | Authenticate with GitHub | Authenticates on GitHub's page and grants consent. |
| 5 | GitHub | Redirect to callback | GitHub redirects the browser to `/login/oauth2/code/github` with an authorisation code. |
| 6 | System | Exchange code for tokens | Spring Security exchanges the authorisation code for an access token. Fetches user info from `https://api.github.com/user`. Extracts `id` (numeric, used as `subId`), `email`, `name`. |
| 7 | System | Validate email | Checks that `email` is non-null and non-blank. If null or blank, redirects to `{frontend-origin}/login?error=github_no_email`. Flow ends. |
| 8 | System | Split name | Splits `name` on the first space. If a space is found: `firstName` = text before the space, `lastName` = text after. If no space (or `name` is null/blank): `firstName` = full value or `"User"`, `lastName` = `""`. |
| 9 | System | Look up ExternalIdentityProvider | Queries `ExternalIdentityProvider` by `(providerType=GITHUB, subId=id)`. |
| 10a | System | Found — load user | Loads the linked `User`. Updates `lastAccessedAt` on the `ExternalIdentityProvider` row. Proceeds to step 12. |
| 10b | System | Not found — look up by email | Queries `User` by `email`. |
| 11a | System | User found by email — link provider | Creates a new `ExternalIdentityProvider` row linked to the existing `User`. Sets `lastAccessedAt` to now. Proceeds to step 12. |
| 11b | System | User not found — auto-register | Creates a `User` (firstName, lastName from step 8, address=null, status=active) + `UserSettings` (feedType=SYNTHETIC) + `ExternalIdentityProvider` row, all in a single transaction. Emits `UserRegistered`. Proceeds to step 12. |
| 12 | System | Issue internal JWT | Generates `{ sub: userId, iat, exp: iat+86400, iss: "trade-platform" }` signed with HMAC-SHA256. |
| 13 | System | Redirect to frontend | Redirects the browser to `{frontend-origin}/auth/callback?token={jwt}`. |
| 14 | Guest Browser | Store token | `AuthCallbackPage` mounts. Reads `token` from the query parameter. |
| 15 | Guest Browser | Establish session | Fetches the full user profile via `GET /api/v1/users/{userId}` (userId decoded from JWT `sub` claim). Stores profile + `accessToken` + `loggedInAt` in the Zustand session store. Persists to `localStorage`. |
| 16 | Guest Browser | Redirect to main page | Navigates to `/trade`. |

## Postconditions

- A `User` record exists for the guest (created or pre-existing).
- An `ExternalIdentityProvider` row exists for `(GITHUB, id)` with `lastAccessedAt` updated.
- The guest holds a valid internal JWT in `localStorage`.
- The Zustand session store contains the full session including `accessToken`.
- `UserRegistered` has been emitted (new users only).
- The guest is on the `/trade` page.

## Error Cases

| Scenario | Condition | System Response | UI Outcome |
|----------|-----------|-----------------|------------|
| GitHub auth denied | Guest denies consent or closes GitHub page | GitHub redirects with `error` param; Spring failure handler fires | Redirect to `{frontend-origin}/login?error=github_oidc_failed`. Login page shows "GitHub authentication failed. Please try again." |
| Token exchange fails | Spring cannot exchange authorisation code for token | Spring OAuth2 failure handler fires | Redirect to `{frontend-origin}/login?error=github_oidc_failed`. Login page shows "GitHub authentication failed. Please try again." |
| No public email | GitHub user-info `email` is null or blank | System redirects at step 7 | Redirect to `{frontend-origin}/login?error=github_no_email`. Login page shows "Your GitHub account has no public email. Please make your email public on GitHub and try again." |
| Internal error | Unexpected error in find-or-register logic | Exception caught by failure handler | Redirect to `{frontend-origin}/login?error=server_error`. Login page shows "Something went wrong. Please try again." |
| Profile fetch fails | `GET /api/v1/users/{userId}` fails at step 15 | HTTP error returned | Session not established. Guest remains on `/auth/callback` with a generic error message. |

## Domain Models Involved

- **User**: Created (step 11b) or loaded (steps 10a, 11a).
- **UserSettings**: Created atomically with `User` at step 11b.
- **ExternalIdentityProvider**: Created (steps 11a, 11b) or updated (step 10a).
- **Session**: Established at step 15.
