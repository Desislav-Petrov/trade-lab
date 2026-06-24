# Account Top-Up

## Overview

Allows an authenticated user to deposit a simulated cash amount into one of their paper trading accounts from the Accounts page. The operation is always accepted instantly. It increases the account's `balance` by the top-up amount and appends an immutable `CREDIT` `LedgerEntry` to the account's ledger history. No FX conversion is involved — the top-up currency always matches the account's base currency.

## Actors

- **Authenticated User**: A logged-in user who wishes to add funds to a specific account.
- **Guest Browser**: The React frontend rendering the Accounts page and top-up modal.
- **System**: The platform backend responsible for validation, persistence, and event emission.

## Preconditions

- The user has an active session (is logged in).
- The user's `status` is `active`.
- The target account exists, belongs to the authenticated user, and has `status: active`.

## Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click "Top Up" on an account | Clicks the Top Up button displayed alongside the target account on the Accounts page. |
| 2 | Guest Browser | Render top-up modal | Opens a modal for the selected account. Displays the account name, currency, and a single numeric input field labelled "Amount". |
| 3 | Authenticated User | Enter top-up amount | Types a whole positive integer into the Amount field. |
| 4 | Guest Browser | Validate input client-side | Enforces: value is a whole number (no decimals), value is between 1 and 10,000,000 inclusive. Displays an inline field error if violated. Blocks submission until the value is valid. |
| 5 | Authenticated User | Click "Confirm" | Submits the top-up request. |
| 6 | Guest Browser | Show loading state | Disables the Confirm button and shows a loading indicator. |
| 7 | System | Validate request | Checks: `amount` is a positive integer, `amount` ≤ 10,000,000, target account exists, account belongs to the authenticated user, account `status` is `active`. |
| 8 | System | Update account balance | Adds `amount` to `Account.balance`. Updates `Account.updatedAt`. |
| 9 | System | Append ledger entry | Creates a new `LedgerEntry` with `type: CREDIT`, `assetType: CASH`, `amount` set to the top-up value, `currency` set to the account's base currency, and `description: "Top-up"`. |
| 10 | System | Emit event | Emits `AccountToppedUp`. |
| 11 | System | Return HTTP 200 | Response body includes `accountId`, `newBalance`, `currency`, `ledgerEntryId`, `timestamp`. |
| 12 | Guest Browser | Show confirmation state | Displays a green tick icon and the message "Top up successful" inside the modal. |
| 13 | Guest Browser | Re-fetch accounts | After confirmation is shown, calls `GET /api/v1/accounts?userId={userId}` to retrieve the latest account data from the backend. |
| 14 | Guest Browser | Update accounts list | Replaces the accounts list with the freshly fetched data. Dismisses the modal. |

## Postconditions

- `Account.balance` has increased by the top-up amount.
- `Account.updatedAt` reflects the time of the operation.
- A new `LedgerEntry` record exists with `type: CREDIT`, `assetType: CASH`, and the correct amount and currency.
- `AccountToppedUp` has been emitted.
- The Accounts page displays the updated balance.

## Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Amount is zero or negative | `amount` ≤ 0 | Client-side: inline field error "Amount must be at least 1." Submit blocked. Backend guardrail: HTTP 400. |
| Amount exceeds maximum | `amount` > 10,000,000 | Client-side: inline field error "Amount must not exceed 10,000,000." Submit blocked. Backend guardrail: HTTP 400. |
| Amount is not a whole number | `amount` contains a decimal | Client-side: inline field error "Amount must be a whole number." Submit blocked. Backend guardrail: HTTP 400. |
| Account not found | `accountId` does not resolve to an account | System returns HTTP 404. Modal shows a generic error message. |
| Account not owned by user | Resolved account's `userId` does not match the session user | System returns HTTP 403. Modal shows a generic error message. |
| Account not active | Account `status` is `suspended` or `closed` | System returns HTTP 403. Modal shows a generic error message. |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |

## Events Emitted

- **AccountToppedUp**: Emitted at step 10. Payload: `accountId`, `userId`, `amount`, `currency`, `newBalance`, `ledgerEntryId`, `timestamp`.

## Domain Models Involved

- **Account**: Read at step 7 for validation; updated at step 8 (`balance`, `updatedAt`).
- **LedgerEntry**: Created at step 9 as an immutable `CREDIT` record.
- **User**: Implicitly validated via the session — `userId` is taken from the authenticated session, not from the request body.
