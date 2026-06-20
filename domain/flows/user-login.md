# User Login

## Overview

Allows a guest to identify themselves and establish a session as a registered user. In this initial implementation, the system presents a list of available email addresses and the guest selects one — no password or credential check is performed. After the backend establishes a session, the frontend fetches the full user profile and caches it in a client-side session store. Authentication will be introduced in a future iteration to replace the email selection step.

## Actors

- **Guest**: An unauthenticated visitor who wishes to log in.
- **System**: The platform backend responsible for retrieving users and establishing a session.
- **Guest Browser**: The React frontend responsible for fetching the user profile and maintaining the client-side session store.

## Preconditions

- At least one user with `active` status exists in the system.

## Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest | Request login | Initiates the login flow. |
| 2 | System | Fetch active users | Retrieves all users with `status` set to `active` and returns their email addresses as a selectable list. |
| 3 | Guest | Select email | Chooses one email address from the list provided. |
| 4 | System | Resolve user | Looks up the user record by the selected `email`. |
| 5 | System | Establish session | Creates a session bound to the resolved `userId`. |
| 6 | System | Emit event | Emits `UserLoggedIn`. |
| 7 | System | Return session | Responds to the guest with `userId` and `email`. |
| 8 | Guest Browser | Fetch full profile | Calls `GET /api/v1/users/{userId}` to retrieve the full user record. |
| 9 | Guest Browser | Establish client session | Stores the full user profile in the Zustand session store. Sets `loggedInAt` to the current client-side timestamp. |
| 10 | Guest Browser | Redirect to main page | Navigates the guest to `/trade`. |

## Postconditions

- An active session exists for the selected user.
- The guest is now authenticated as that user and may access the platform.
- The full user profile is cached in the frontend Zustand session store.
- `UserLoggedIn` has been emitted.
- The guest is on the `/trade` page.

## Events Emitted

- **UserLoggedIn**: Emitted at step 6. Payload: `userId`, `email`, `timestamp`.

## Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| No active users | No users with `active` status exist | Flow halts at step 2; system returns an empty list and surfaces an informational message. |
| User not found | Selected email does not resolve to a user record | Flow halts at step 4; system returns an error. |
| User not active | Resolved user has `status` of `suspended` or `closed` | Flow halts at step 4; system returns an error indicating the account is unavailable. |
| Profile fetch fails | `GET /api/v1/users/{userId}` returns an error at step 8 | Session is not established; guest remains on the login screen with a generic error message. |

## Domain Models Involved

- **User**: Read at step 2 to populate the email list, and at step 4 to resolve the selected email to a full user record. Read again at step 8 to populate the client session.
- **Session**: Written at step 9 with the full user profile.

## Notes

> **Temporary implementation.** The email selection step (steps 2–3) is a placeholder. Once authentication is introduced, it will be replaced with a credential submission and verification step. Steps 5 onwards are expected to remain unchanged.
