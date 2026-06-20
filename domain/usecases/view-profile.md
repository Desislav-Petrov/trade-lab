# Use Case: View Profile

## Goal

An authenticated user views their full account information on the profile page.

## Actor

Authenticated User — a guest who has completed the login flow and has an active
session in the Zustand store.

## Screen

- **Route:** `/profile`
- **Page:** `ProfilePage`
- **Entry point:** Authenticated user clicks the Profile link in the sidebar.

## Trigger

Authenticated user clicks the Profile nav link in the sidebar or navigates
directly to `/profile`.

## Domain Models

- `domain/model/session`
- `domain/model/user`

## Flows

- `domain/flows/user-session` (Flow B — View Profile Page)

## Happy Path

1. Authenticated user clicks Profile in the sidebar.
2. Frontend reads the full user profile from the Zustand session store.
3. Profile page renders: `firstName`, `lastName`, `email`, `address`, `status`, `createdAt` (formatted as local date), and the current date.

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| No active session | User is redirected to `/login`. |

## Out of Scope

- Editing profile fields.
- Changing email address.
- Uploading a profile photo.
- Deleting the account.
