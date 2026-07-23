# Update User Settings

## Overview

Allows an authenticated user to update their platform-level settings from the Platform Settings tab on the Profile page. In this iteration the only configurable setting is `feedType` (`SYNTHETIC` | `REAL`). The endpoint is designed to accept a partial settings object so that future settings can be added without breaking existing clients. A successful update persists the change, refreshes `updatedAt`, and emits `UserSettingsChangedEvent` with the full settings snapshot.

## Actors

- **Authenticated User**: A logged-in user who wishes to change a platform setting.
- **Guest Browser**: The React frontend rendering the Platform Settings tab and sending the PATCH request.
- **System**: The platform backend (User domain) responsible for validation, persistence, and event emission.

## Preconditions

- The user has an active session (is logged in).
- The user's `status` is `active`.
- A `UserSettings` row exists for the user (guaranteed since registration).

## Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Navigate to Profile → Platform Settings tab | Clicks the Profile link in the sidebar, then selects the Platform Settings tab. |
| 2 | Guest Browser | Render Platform Settings tab | Displays the General Platform Settings section. Renders a labelled dropdown for "Market Data Feed" pre-populated with the user's current `feedType` value sourced from the session store (loaded inline with the user profile at login). Options: "Synthetic Feed", "Real Market Feed". Each option has a tooltip explaining what it does. |
| 3 | Authenticated User | Select a feed type from the dropdown | Selects either "Synthetic Feed" or "Real Market Feed". |
| 4 | Guest Browser | Send PATCH request | Immediately on selection change (no separate Save button), calls `PATCH /api/v1/users/{userId}/settings` with body `{ "feedType": "<selected value>" }` and the authenticated user's session token. Shows a loading/saving indicator on the dropdown. |
| 5 | System | Authenticate request | Validates the session. Returns HTTP 401 if no valid session. |
| 6 | System | Authorise request | Confirms the `userId` in the path matches the authenticated user's session. Returns HTTP 403 if they differ. |
| 7 | System | Validate request body | Checks that `feedType` (if present) is one of `SYNTHETIC` or `REAL`. Returns HTTP 400 for unknown values. Ignores unknown fields in the request body — they are silently discarded. |
| 8 | System | Load current settings | Reads the `UserSettings` row for the user. |
| 9 | System | Apply update | Updates the supplied fields on the `UserSettings` row. Sets `updatedAt` to current server timestamp. Persists the change. |
| 10 | System | Emit event | Emits `UserSettingsChangedEvent` with payload: `userId`, `feedType` (current value after update), `updatedAt`. |
| 11 | System | Return HTTP 200 | Response body: the full updated settings object `{ "feedType": "<value>", "updatedAt": "<timestamp>" }`. |
| 12 | Guest Browser | Update session store | Replaces the `settings` field in the Zustand session store with the returned settings object so the UI reflects the change immediately without a full profile re-fetch. |
| 13 | Guest Browser | Show success indicator | Removes the loading indicator. Shows a brief "Saved" confirmation (e.g. a green tick or toast). |

## Postconditions

- The `UserSettings` row reflects the new value and an updated `updatedAt`.
- `UserSettingsChangedEvent` has been emitted with the full settings snapshot.
- The Platform Settings tab shows the newly selected value.
- The Zustand session store's `settings` field reflects the updated value.

## Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Invalid feedType value | `feedType` not in `[SYNTHETIC, REAL]` | System returns HTTP 400. Frontend shows inline error on the dropdown: "Invalid feed type selected." Dropdown reverts to the previous value. |
| User not found | `userId` in path does not resolve | System returns HTTP 404. Frontend shows a generic error message. |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |
| Unauthorised request | Path `userId` does not match session user | System returns HTTP 403. Frontend shows a generic error message. |
| Server error | Any unhandled backend failure | System returns HTTP 500. Frontend shows a generic error message. Dropdown reverts to the previous value. |

## Events Emitted

- **UserSettingsChangedEvent**: Emitted at step 10. Payload: `userId`, `feedType`, `updatedAt`.

## Domain Models Involved

- **UserSettings**: Read at step 8; updated at step 9. All fields written.
- **User**: Implicitly validated via the session — `userId` is taken from the path and cross-checked against the authenticated session.
