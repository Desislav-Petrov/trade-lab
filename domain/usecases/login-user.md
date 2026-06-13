# Use Case: Login a User

## Goal

A guest identifies themselves as a registered user and gains access to the platform.

## Actor

Guest — an unauthenticated visitor with an existing user account.

## Trigger

Guest navigates to the login screen and initiates the login process.

## Domain Models

- `models/user`

## Flows

- `flows/user-login`

## Happy Path

1. Guest navigates to the login screen.
2. System displays a list of available email addresses belonging to `active` users.
3. Guest selects their email address from the list.
4. System resolves the selected email to a user record and establishes a session.
5. Guest is logged in and gains access to the platform.

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| No active users exist | System displays an empty list with an informational message; login cannot proceed. |
| Selected email does not resolve to a user | System returns an error; session is not created. |
| Resolved user is suspended or closed | System returns an error indicating the account is unavailable. |

## Out of Scope

- Password or credential verification (authentication is not implemented in this iteration).
- Session expiry or renewal.
- Logout.
- Multi-factor authentication.
- Account recovery or password reset.
