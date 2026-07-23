# Session

## Overview

Represents the authenticated state of a user on the frontend. Holds the full user profile and the internal JWT access token. Persisted to `localStorage` so the session survives page refreshes. The session is cleared on logout.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| userId | uuid | yes | ID of the authenticated user |
| firstName | string | yes | User's first name |
| lastName | string | yes | User's last name |
| address | string | **no** | User's postal address. May be `null` for OIDC-registered users. |
| email | string | yes | User's email address |
| status | enum | yes | `active` \| `suspended` \| `closed` |
| createdAt | datetime | yes | Timestamp of the user's account creation |
| loggedInAt | datetime | yes | Client-side timestamp of when the session was established |
| accessToken | string | yes | Internal JWT issued by the backend after successful OIDC authentication. Used as the `Authorization: Bearer` value on all subsequent API requests. |

## Behaviors

- **Establish**: Populated after OIDC login completes and the internal JWT is received via the `/auth/callback?token=` redirect. Sets `loggedInAt` to the current client-side timestamp. Persists the full session (including `accessToken`) to `localStorage`.
- **Restore**: On application load, reads `localStorage` to restore a previously established session. If the stored token is present and its `exp` claim has not passed (checked client-side without a network call), the session is restored. If the token is expired or absent, the session is not restored and the user is redirected to `/login`.
- **Clear**: Removes all session data from the Zustand store and from `localStorage`. Triggered by logout.

## Relationships

- **User** (`reference`): The session mirrors the `User` entity. `userId` references the backend `User` record. No foreign key — reference by ID only.

## Business Rules

- A session may only be established for a user with `active` status.
- Only one session may exist in the store at a time. Establishing a new session overwrites any existing one.
- Session data (including the JWT) is persisted to `localStorage` and survives page refreshes.
- The `accessToken` is attached as `Authorization: Bearer {token}` to every API request via the shared Axios request interceptor.
- If `localStorage` contains a session whose token `exp` is in the past on application load, the session is not restored and the user is redirected to `/login`.
- Session data must never be written to the backend.
