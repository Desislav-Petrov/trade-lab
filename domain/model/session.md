# Session

## Overview

Represents the authenticated state of a user on the frontend. Holds the full
user profile fetched after a successful login. Lives exclusively in the
frontend Zustand store — it is never persisted to the backend database.
The session is cleared on logout or page refresh.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| userId | uuid | yes | ID of the authenticated user |
| firstName | string | yes | User's first name |
| lastName | string | yes | User's last name |
| address | string | yes | User's postal address |
| email | string | yes | User's email address |
| status | enum | yes | `active` \| `suspended` \| `closed` |
| createdAt | datetime | yes | Timestamp of the user's account creation |
| loggedInAt | datetime | yes | Client-side timestamp of when the session was established |

## Behaviors

- **Establish**: Populated after login completes and the full user profile is
  fetched. Sets `loggedInAt` to the current client-side timestamp.
- **Clear**: Removes all session data from the store. Triggered by logout.

## Relationships

- **User** (`reference`): The session mirrors the `User` entity. `userId`
  references the backend `User` record. No foreign key — reference by ID only.

## Business Rules

- A session may only be established for a user with `active` status.
- Only one session may exist in the store at a time. Establishing a new session
  overwrites any existing one.
- Session data is frontend-only and must never be written to the backend.
- The session is not persisted across page refreshes in this iteration.
