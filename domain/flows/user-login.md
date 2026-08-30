# User Login

## Overview

Allows a guest to identify themselves by selecting their email address and establish a fully authenticated session on the platform. The backend issues the same internal JWT as the OIDC login flows. The frontend callback path is identical to the OAuth2 callback path.

> **Note:** This email-selection flow is retained for local testing only. No password or credential check is performed. The primary authentication path for real users is `domain/flows/oidc-login` (Google) or `domain/flows/github-oidc-login` (GitHub).
>
> **Environment gate:** This flow is only available when `ENABLE_NO_AUTH=true`. When the flag is absent or `false`, the backend endpoints (`POST /api/v1/users/login`, `GET /api/v1/users/emails`) are not registered and the frontend `/login` route does not exist. See `decisions/2026-08-30-enable-no-auth-flag.md`.

## Actors

- **Guest**: An unauthenticated visitor who wishes to log in.
- **System**: The platform backend responsible for retrieving users, issuing the JWT, and redirecting to the frontend callback.
- **Guest Browser**: The React frontend responsible for handling the callback, storing the token, fetching the user profile, and maintaining the client-side session store.

## Preconditions

- `ENABLE_NO_AUTH=true` is set in the environment (backend and frontend build).
- At least one user with `active` status exists in the system.

## Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest | Request login | Navigates to `/login`. |
| 2 | System | Fetch active users | Retrieves all users with `status` set to `active` and returns their email addresses as a selectable list. |
| 3 | Guest | Select email | Chooses one email address from the list provided. |
| 4 | System | Resolve user | Looks up the user record by the selected `email`. |
| 5 | System | Issue internal JWT | Generates `{ sub: userId, iat, exp: iat+86400, iss: "trade-platform" }` signed with HMAC-SHA256. |
| 6 | System | Emit event | Emits `UserLoggedIn`. |
| 7 | System | Redirect to frontend callback | Redirects the browser to `{frontend-origin}/auth/callback?token={jwt}`. |
| 8 | Guest Browser | Read token | `AuthCallbackPage` mounts. Reads `token` from the query parameter. |
| 9 | Guest Browser | Establish session | Decodes the JWT `sub` claim to obtain `userId`. Calls `GET /api/v1/users/{userId}` to fetch the full user profile. Stores profile + `accessToken` + `loggedInAt` in the Zustand session store. Persists to `localStorage`. |
| 10 | Guest Browser | Redirect to main page | Navigates to `/trade`. |

## Postconditions

- The guest holds a valid internal JWT in `localStorage`.
- The Zustand session store contains the full session including `accessToken`.
- `UserLoggedIn` has been emitted.
- The guest is on the `/trade` page.

## Events Emitted

- **UserLoggedIn**: Emitted at step 6. Payload: `userId`, `email`, `timestamp`.

## Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|----------|
| No active users | No users with `active` status exist | Flow halts at step 2; system returns an empty list and surfaces an informational message. |
| User not found | Selected email does not resolve to a user record | Flow halts at step 4; system returns an error. |
| User not active | Resolved user has `status` of `suspended` or `closed` | Flow halts at step 4; system returns an error indicating the account is unavailable. |
| Profile fetch fails | `GET /api/v1/users/{userId}` returns an error at step 9 | Session is not established; guest remains on `/auth/callback` with a generic error message. |

## Domain Models Involved

- **User**: Read at step 2 to populate the email list, and at step 4 to resolve the selected email to a full user record.
- **Session**: Established at step 9 with the full user profile and access token.
