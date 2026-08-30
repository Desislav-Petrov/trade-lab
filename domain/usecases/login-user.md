# Use Case: Login a User

## Goal

A guest identifies themselves as a registered user, gains a JWT-authenticated session, and has their full profile cached in the frontend session store.

## Actor

Guest — an unauthenticated visitor with an existing user account.

## Trigger

Guest navigates to `/login`, selects their email address from the list, and submits.

## Domain Models

- `domain/model/user`
- `domain/model/session`

## Flows

- `domain/flows/user-login`
- `domain/flows/jwt-authentication`
- `domain/flows/user-session` (Flow A — Session UI Display)

## Happy Path

1. Guest navigates to `/login`.
2. System displays a dropdown list of email addresses belonging to `active` users.
3. Guest selects their email address and submits.
4. System resolves the email to a user record, issues a 24h internal JWT, emits `UserLoggedIn`, and redirects to `/auth/callback?token={jwt}`.
5. `AuthCallbackPage` reads the token, decodes `userId` from the JWT `sub` claim.
6. Frontend calls `GET /api/v1/users/{userId}` to fetch the full user profile.
7. Frontend stores the full user profile + `accessToken` + `loggedInAt` in the Zustand session store. Persists to `localStorage`.
8. Topbar updates to show "Logged in as [firstName] [lastName]" and a Logout button.
9. Sidebar shows a Profile nav link.
10. Guest is redirected to `/trade`.

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| No active users exist | System displays an empty list with an informational message; login cannot proceed. |
| Selected email does not resolve to a user | System returns an error; redirect does not occur. |
| Resolved user is suspended or closed | System returns an error indicating the account is unavailable; redirect does not occur. |
| Profile fetch (`GET /api/v1/users/{userId}`) fails | Session is not established; guest remains on `/auth/callback` with a generic error message. |

## Out of Scope

- Password or credential verification (this is a local-testing flow only).
- Session expiry or renewal.
- Multi-factor authentication.
- Account recovery or password reset.
