# Open Account

## Overview

Allows an authenticated user to open a new paper trading account from the Accounts page. The user selects a base currency and optionally provides a name. The account is created with zero balance and an empty ledger history. This flow does not move any funds — top-up is a separate use case.

## Actors

- **Authenticated User**: A logged-in user who wishes to open a new account.
- **Guest Browser**: The React frontend rendering the Accounts page and open-account form.
- **System**: The platform backend responsible for validation and persistence.

## Preconditions

- The user has an active session (is logged in).
- The user's `status` is `active`.

## Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Navigate to `/accounts` | Arrives at the Accounts page via the Accounts link in the sidebar. |
| 2 | Guest Browser | Render Accounts page | Displays the list of existing accounts (empty if none) and an "Open new account" button. |
| 3 | Authenticated User | Click "Open new account" | Opens the account creation form (inline or modal). |
| 4 | Guest Browser | Render account creation form | Displays a currency selector (`USD`, `GBP`, `EUR`) and an optional name field. |
| 5 | Authenticated User | Select base currency | Chooses one of the supported currencies. Required. |
| 6 | Authenticated User | Enter account name (optional) | Provides a label. If left blank, the system will default to `account-{id}`. |
| 7 | Authenticated User | Click "Open account" | Submits the form. |
| 8 | Guest Browser | Show loading state | Disables the submit button and shows a loading indicator. |
| 9 | System | Validate request | Checks that `userId` resolves to an `active` user and that `currency` is one of the supported values. |
| 10 | System | Create account record | Generates a new `id` (UUID), sets `balance` to `0`, `status` to `active`, resolves `name` (supplied label or `account-{id}`), and persists the record. |
| 11 | System | Emit event | Emits `AccountOpened`. |
| 12 | System | Return HTTP 201 | Response body includes `accountId`, `name`, `currency`, `balance`, `status`, `createdAt`. |
| 13 | Guest Browser | Update accounts list | Adds the newly created account to the list displayed on the Accounts page without a full page reload. |
| 14 | Guest Browser | Close form | Dismisses the creation form and returns focus to the Accounts page. |

## Postconditions

- An `Account` record exists with `status: active`, `balance: 0`, and an empty ledger history.
- `AccountOpened` has been emitted.
- The new account is visible in the user's accounts list.

## Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|----------|
| No currency selected | `currency` is absent | Form-level validation error: "Please select a base currency." Submit blocked. |
| Unsupported currency | `currency` is not `USD`, `GBP`, or `EUR` | System returns HTTP 400. Form-level error displayed. |
| User not active | Resolved user has `status` of `suspended` or `closed` | System returns HTTP 403. Generic error displayed; form dismissed. |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |

## Events Emitted

- **AccountOpened**: Emitted at step 11. Payload: `accountId`, `userId`, `currency`, `name`, `timestamp`.

## Domain Models Involved

- **Account**: Created at step 10.
- **User**: Read at step 9 to validate `userId` and `status`.
- **LedgerEntry**: Not involved in this flow — the ledger history starts empty.
