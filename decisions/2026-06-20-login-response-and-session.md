# Decision: Login Response Shape and Frontend Session Strategy

**Date:** 2026-06-20
**Status:** accepted

## Context

The login use case requires that after a successful login, the frontend displays
the user's full name and email in the topbar and provides a dedicated profile
page showing all user fields. Two approaches were considered:

1. **Extend the login response** — have `POST /api/v1/users/login` return the
   full user object (`firstName`, `lastName`, `address`, `status`, `createdAt`)
   in addition to `userId` and `email`.

2. **Separate profile fetch** — keep the login response minimal (`userId`,
   `email`) and have the frontend make a subsequent `GET /api/v1/users/{userId}`
   call to retrieve the full profile.

## Decision

Option 2 — separate profile fetch — was chosen.

The login endpoint's responsibility is to authenticate and return a session
identifier. Returning the full user profile from the login endpoint conflates
authentication with profile retrieval and violates single-responsibility. The
`GET /api/v1/users/{userId}` endpoint is a natural, reusable fetch that serves
both the post-login profile cache and any future re-fetch requirements (e.g.
after profile edits).

The full user profile is cached in a **frontend-only Zustand store** (the
`Session` entity). No backend session store is introduced at this stage. This
is consistent with the existing architecture where no server-side session
mechanism exists.

## Consequences

- `POST /api/v1/users/login` response shape remains `{ userId, email }` — no
  change to the existing contract.
- `GET /api/v1/users/{userId}` must be added to the user domain OpenAPI
  contract and implemented in the backend.
- The frontend must make two sequential calls on login: POST login → GET
  profile. A failure at either step prevents session establishment.
- The `Session` model is frontend-only. It must never be persisted to the
  backend database.
- Session is not persisted across page refreshes in this iteration. If
  persistence is required in future, a decision log entry must be added and a
  strategy chosen (e.g. localStorage, server-side sessions, JWT).
