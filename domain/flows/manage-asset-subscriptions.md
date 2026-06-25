# Manage Asset Subscriptions

## Overview

Covers the three discrete operations a user performs on their stock watchlist from the Stock Trading page: loading their current subscriptions on page entry, bulk-adding new ticker subscriptions, and bulk-removing existing ones. All operations are scoped to the authenticated user and are backed by REST APIs in the Market Data domain. The supported ticker list is sourced from a static configuration file loaded at application startup.

---

## Flow A — Load Subscriptions

Fetches and displays the authenticated user's current asset subscriptions when they enter the Stock Trading page.

### Actors

- **Authenticated User**: A logged-in user navigating to `/trade`.
- **Guest Browser**: The React frontend rendering the Stock Trading page.
- **System**: The Market Data backend returning the user's subscriptions.

### Preconditions

- The user has an active session (is logged in).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Navigate to `/trade` | Arrives at the Stock Trading page via the Stock Trading link in the sidebar. |
| 2 | Guest Browser | Fetch subscriptions | Calls `GET /api/v1/market-data/subscriptions` with the authenticated `userId`. |
| 3 | System | Resolve subscriptions | Queries all `AssetSubscription` records for the user, ordered by `ticker` ascending. |
| 4 | System | Return HTTP 200 | Response body: array of `{ ticker, companyName }` objects. Empty array if no subscriptions exist. |
| 5 | Guest Browser | Render subscription list | Displays each subscription as a row showing `ticker` and `companyName`. If the list is empty, displays an empty-state message. |

### Postconditions

- The Stock Trading page shows the user's current subscriptions (or an empty state).

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |
| Server error | Backend fails to query subscriptions | System returns HTTP 500. Frontend displays a generic error message. |

---

## Flow B — Bulk Add Subscriptions

The user selects one or more tickers from the supported list and adds them to their watchlist in a single action.

### Actors

- **Authenticated User**: A logged-in user adding tickers to their watchlist.
- **Guest Browser**: The React frontend rendering the add-subscription panel.
- **System**: The Market Data backend validating and persisting the new subscriptions.

### Preconditions

- The user has an active session (is logged in).
- The Stock Trading page is loaded (Flow A has completed).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Open add panel | Clicks "Add tickers" to open the subscription selection panel. |
| 2 | Guest Browser | Render supported ticker list | Displays the full list of supported tickers (sourced from static config), excluding tickers the user is already subscribed to. Each entry shows `ticker` and `companyName`. |
| 3 | Authenticated User | Filter tickers (optional) | Types in the filter input. The displayed list is narrowed in real-time client-side to tickers whose `ticker` field starts with or contains the typed string (case-insensitive). |
| 4 | Authenticated User | Select tickers | Selects one or more tickers from the filtered list using checkboxes or multi-select. |
| 5 | Authenticated User | Click "Add" | Submits the selection. |
| 6 | Guest Browser | Show loading state | Disables the Add button and shows a loading indicator. |
| 7 | System | Validate request | Checks that: (a) each ticker exists in the supported config, (b) none of the tickers are already subscribed by this user, (c) adding them would not push the user's total subscription count above 1000. |
| 8 | System | Persist subscriptions | Creates one `AssetSubscription` record per selected ticker for this user. `companyName` is resolved from config at this point. |
| 9 | System | Emit event | Emits `AssetSubscribedEvent`. |
| 10 | System | Return HTTP 201 | Response body: array of newly created `{ ticker, companyName }` objects. |
| 11 | Guest Browser | Update subscription list | Adds the new entries to the displayed subscription list without a full page reload. Closes the add panel. |

### Postconditions

- One `AssetSubscription` record exists per newly added ticker for this user.
- `AssetSubscribedEvent` has been emitted.
- The Stock Trading page subscription list reflects the additions.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| No tickers selected | User clicks Add with nothing selected | Frontend-level validation: Add button remains disabled until at least one ticker is selected. |
| Ticker already subscribed | One or more selected tickers already exist in the user's subscriptions | System returns HTTP 409. Frontend displays an error message; no subscriptions are created. |
| Ticker not in supported list | One or more selected tickers do not exist in the config | System returns HTTP 400. Frontend displays an error message; no subscriptions are created. |
| Subscription limit reached | Adding the selected tickers would exceed 1000 total | System returns HTTP 422. Frontend displays: "Adding these tickers would exceed your 1000 subscription limit." |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |
| Server error | Backend fails during persistence | System returns HTTP 500. Frontend displays a generic error message; no subscriptions are created. |

---

## Flow C — Bulk Remove Subscriptions

The user selects one or more of their existing subscriptions and removes them in a single action.

### Actors

- **Authenticated User**: A logged-in user removing tickers from their watchlist.
- **Guest Browser**: The React frontend rendering the subscription list with removal controls.
- **System**: The Market Data backend deleting the selected subscription records.

### Preconditions

- The user has an active session (is logged in).
- The Stock Trading page is loaded and shows at least one subscription (Flow A has completed).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Select subscriptions to remove | Selects one or more existing subscriptions from the list using checkboxes. |
| 2 | Authenticated User | Click "Remove" | Submits the removal request. |
| 3 | Guest Browser | Show loading state | Disables the Remove button and shows a loading indicator. |
| 4 | System | Validate request | Checks that each submitted ticker corresponds to an active `AssetSubscription` for this user. |
| 5 | System | Delete subscriptions | Permanently deletes each matching `AssetSubscription` record. |
| 6 | System | Emit event | Emits `AssetUnsubscribedEvent`. |
| 7 | System | Return HTTP 204 | No response body. |
| 8 | Guest Browser | Update subscription list | Removes the deleted entries from the displayed list without a full page reload. Clears the selection state. |

### Postconditions

- The selected `AssetSubscription` records no longer exist in the database.
- `AssetUnsubscribedEvent` has been emitted.
- The Stock Trading page subscription list no longer shows the removed tickers.
- If all subscriptions were removed, the empty-state message is displayed.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| No tickers selected | User clicks Remove with nothing selected | Frontend-level validation: Remove button remains disabled until at least one subscription is selected. |
| Ticker not found | One or more submitted tickers do not exist in the user's subscriptions | System returns HTTP 404. Frontend displays an error message; no records are deleted. |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |
| Server error | Backend fails during deletion | System returns HTTP 500. Frontend displays a generic error message; no records are deleted. |

---

## Events Emitted

- **AssetSubscribedEvent**: Emitted at step 9 of Flow B. Payload: `userId`, `tickers` (list of added ticker symbols), `timestamp`.
- **AssetUnsubscribedEvent**: Emitted at step 6 of Flow C. Payload: `userId`, `tickers` (list of removed ticker symbols), `timestamp`.

---

## Domain Models Involved

- **AssetSubscription**: Read in Flow A; created in Flow B; deleted in Flow C.
- **Session**: Read in all flows to resolve the authenticated `userId`.
