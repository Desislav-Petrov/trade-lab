# OIDC Login

## Overview

Allows a guest to authenticate using an external identity provider (currently Google) and establish a session on the paper trading platform. On first use, a user profile is auto-created from the provider's ID token claims. On subsequent uses, the existing profile is loaded. In both cases the backend issues an internal JWT which the frontend stores in `localStorage`.

This flow runs entirely via a backend-driven OAuth2 dance — the frontend never handles the identity provider token directly.

## Actors

- **Guest**: An unauthenticated visitor who chooses to log in via Google.
- **Guest Browser**: The React frontend rendering the login page and handling the post-callback redirect.
- **System**: The Spring Boot backend — Spring Security OAuth2 filter chain, `OidcAuthService`, `JwtService`.
- **Google**: The external identity provider.

## Preconditions

- The guest has a Google account.
- The backend is configured with a valid Google OAuth2 client ID and secret.

## Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest Browser | Render login page | Displays the "Login with Google" button at `/login` alongside the existing email-select option (retained for testing only). |
| 2 | Guest | Click "Login with Google" | Triggers a browser redirect to `/oauth2/authorization/google`. |
| 3 | System | Redirect to Google | Spring Security redirects the browser to Google's OAuth2 authorisation endpoint with the configured client ID, scopes (`openid email profile`), and redirect URI. |
| 4 | Guest | Authenticate with Google | Authenticates on Google's page and grants consent. |
| 5 | Google | Redirect to callback | Google redirects the browser to `/login/oauth2/code/google` with an authorisation code. |
| 6 | System | Exchange code for tokens | Spring Security exchanges the authorisation code for an ID token and access token. Validates the ID token signature and claims. Extracts `sub`, `email`, `given_name`, `family_name`. |
| 7 | System | Look up ExternalIdentityProvider | Queries `ExternalIdentityProvider` by `(providerType=GOOGLE, subId=sub)`. |
| 8a | System | Found — load user | Loads the linked `User`. Updates `lastAccessedAt` on the `ExternalIdentityProvider` row. Proceeds to step 10. |
| 8b | System | Not found — look up by email | Queries `User` by `email`. |
| 9a | System | User found by email — link provider | Creates a new `ExternalIdentityProvider` row linked to the existing `User`. Sets `lastAccessedAt` to now. Proceeds to step 10. |
| 9b | System | User not found — auto-register | Creates a `User` (firstName=given_name, lastName=family_name, address=null, status=active) + `UserSettings` (feedType=SYNTHETIC) + `ExternalIdentityProvider` row, all in a single transaction. Emits `UserRegistered`. Proceeds to step 10. |
| 10 | System | Issue internal JWT | Generates `{ sub: userId, iat, exp: iat+86400, iss: "trade-platform" }` signed with HMAC-SHA256. |
| 11 | System | Redirect to frontend | Redirects the browser to `{frontend-origin}/auth/callback?token={jwt}`. |
| 12 | Guest Browser | Store token | `AuthCallbackPage` mounts. Reads `token` from the query parameter. |
| 13 | Guest Browser | Establish session | Fetches the full user profile via `GET /api/v1/users/{userId}` (userId decoded from JWT `sub` claim). Stores profile + `accessToken` + `loggedInAt` in the Zustand session store. Persists to `localStorage`. |
| 14 | Guest Browser | Redirect to main page | Navigates to `/trade`. |

## Postconditions

- A `User` record exists for the guest (created or pre-existing).
- An `ExternalIdentityProvider` row exists for `(GOOGLE, sub)` with `lastAccessedAt` updated.
- The guest holds a valid internal JWT in `localStorage`.
- The Zustand session store contains the full session including `accessToken`.
- `UserRegistered` has been emitted (new users only).
- The guest is on the `/trade` page.

## Error Cases

| Scenario | Condition | System Response | UI Outcome |
|----------|-----------|-----------------|------------|
| Google auth denied | Guest denies consent or closes Google page | Google redirects with `error` param; Spring failure handler fires | Redirect to `{frontend-origin}/login?error=oidc_failed`. Login page shows "Authentication failed. Please try again." |
| Token exchange fails | Spring cannot exchange authorisation code for token | Spring OAuth2 failure handler fires | Redirect to `{frontend-origin}/login?error=oidc_failed` |
| Internal error | Unexpected error in find-or-register logic | Exception caught by failure handler | Redirect to `{frontend-origin}/login?error=server_error`. Login page shows a generic error message. |
| Profile fetch fails | `GET /api/v1/users/{userId}` fails at step 13 | HTTP error returned | Session not established. Guest remains on `/auth/callback` with a generic error message. |

## Domain Models Involved

- **User**: Created (step 9b) or loaded (steps 8a, 9a).
- **UserSettings**: Created atomically with `User` at step 9b.
- **ExternalIdentityProvider**: Created (steps 9a, 9b) or updated (step 8a).
- **Session**: Established at step 13.
