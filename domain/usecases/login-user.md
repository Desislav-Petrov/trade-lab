# Use Case: Login a User

## Goal

A guest identifies themselves as a registered user, gains access to the
platform, and has their full profile cached in the frontend session store.

## Actor

Guest — an unauthenticated visitor with an existing user account.

## Trigger

Guest navigates to the login screen and initiates the login process.

## Domain Models

- `domain/model/user`
- `domain/model/session`

## Flows

- `domain/flows/user-login`
- `domain/flows/user-session` (Flow A — Session UI Display)

## Happy Path

1. Guest navigates to `/login`.
2. System displays a dropdown list of email addresses belonging to `active` users.
3. Guest selects their email address from the list and submits.
4. System resolves the selected email to a user record and establishes a backend session. Returns `userId` and `email`.
5. Frontend calls `GET /api/v1/users/{userId}` to fetch the full user profile.
6. Frontend stores the full user profile in the Zustand session store.
7. Topbar updates to show "Logged in as [firstName] [lastName]" and a Logout button.
8. Sidebar shows a Profile nav link.
9. Guest is redirected to `/trade`.

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| No active users exist | System displays an empty list with an informational message; login cannot proceed. |
| Selected email does not resolve to a user | System returns an error; session is not created. |
| Resolved user is suspended or closed | System returns an error indicating the account is unavailable. |
| Profile fetch (`GET /api/v1/users/{userId}`) fails | Session is not established; guest remains on login screen with a generic error message. |

## Out of Scope

- Password or credential verification (authentication is not implemented in this iteration).
- Session expiry or renewal.
- Multi-factor authentication.
- Account recovery or password reset.
- Session persistence across page refreshes.
