# Select Trading Account

## Overview

Allows an authenticated user on the Stock Trading page (`/trade`) to select which of their active paper trading accounts will fund stock purchases. The selection is held in the `stocktrading` Zustand slice for the duration of the session. On page mount the frontend fetches the user's active accounts from the backend; the first account in the returned list is set as the default if no selection is already stored. If the user has no active accounts, no default is set and the selector is empty.

This flow is preparatory — it establishes the funding account for the upcoming stock-buy flow. No funds are moved and no orders are placed here.

## Actors

- **Authenticated User**: A logged-in user navigating the Stock Trading page who wishes to choose a funding account.
- **Guest Browser**: The React frontend rendering the account selector on `/trade`.
- **System**: The platform backend serving the list of accounts for the authenticated user.

## Preconditions

- The user has an active session (is logged in).
- The user is on the `/trade` page.

## Steps

### Flow A — Page Mount: Load and Default Account

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest Browser | Mount `/trade` page | The `TradeStockPage` component mounts. |
| 2 | Guest Browser | Fetch active accounts | Calls `GET /api/v1/accounts?userId={userId}&status=active`. TanStack Query fetches on every mount (default `staleTime: 0`). |
| 3 | System | Return active accounts | Returns the list of accounts belonging to the authenticated user that have `status: active`, ordered by `createdAt` ascending. |
| 4 | Guest Browser | Apply default selection | If no account ID is already stored in the `stocktrading` Zustand slice, sets the selected account ID to the `id` of the first account in the returned list. If the list is empty, no default is set. |
| 5 | Guest Browser | Render account selector | Displays a dropdown/selector showing all active accounts (name + currency). The currently selected account is highlighted. If the list is empty, the selector renders an empty state: "No accounts available. Open an account first." |

### Flow B — User Selects an Account

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click the account selector | Opens the dropdown of active accounts. |
| 2 | Authenticated User | Choose an account | Selects one account from the list. |
| 3 | Guest Browser | Persist selection | Stores the selected account's `id` in the `stocktrading` Zustand slice. |
| 4 | Guest Browser | Update selector display | The selector reflects the newly chosen account immediately. |

## Postconditions

- The `stocktrading` Zustand slice holds the `id` of the currently selected active account, or is empty if no active accounts exist.
- The selected account persists for the duration of the browser session — navigating away from and back to `/trade` preserves the last selection.
- On each mount, the account list is refreshed from the API. If the list is empty the selection is not altered.

## Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Fetch fails | `GET /api/v1/accounts` returns a non-2xx response | Selector shows an error state: "Could not load accounts." No selection change is made. |
| No active accounts | Returned list is empty | Selector renders empty state message. No default is set. Existing selection (if any) is preserved. |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |

## Domain Models Involved

- **Account**: Read at Flow A step 3 — filtered to `status: active`, ordered by `createdAt` ascending. Fields used: `id`, `name`, `currency`.
- **Session**: Read at Flow A step 2 — `userId` sourced from the Zustand session slice to scope the accounts query.
