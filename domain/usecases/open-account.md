# Use Case: Open an Account

## Goal

An authenticated user opens a new paper trading account with a chosen base currency and an optional name, then sees it appear in their accounts list.

## Actor

Authenticated User — a logged-in user with `active` status.

## Screen

- **Route:** `/accounts`
- **Page:** `AccountsPage`
- **Entry point:** Authenticated user clicks the Accounts link in the sidebar.

## Trigger

Authenticated user clicks "Open new account" on the Accounts page and submits the creation form.

## Domain Models

- `domain/model/account`
- `domain/model/ledger-entry`
- `domain/model/user`

## Flows

- `domain/flows/open-account`
- `domain/flows/user-session` (Flow A — Session UI Display, for the Accounts sidebar link)

## Happy Path

1. Authenticated user navigates to `/accounts` via the Accounts sidebar link.
2. Accounts page renders the user's existing accounts (empty list if none) and an "Open new account" button.
3. User clicks "Open new account" — account creation form appears with a currency selector (`USD`, `GBP`, `EUR`) and an optional name field.
4. User selects a currency, optionally enters a name, and clicks "Open account".
5. System validates the request, creates the account with `balance: 0` and `status: active`, and emits `AccountOpened`.
6. New account appears in the accounts list. Form is dismissed.

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| No currency selected | Inline validation error: "Please select a base currency." Form not submitted. |
| Unsupported currency | System returns HTTP 400. Form-level error shown. |
| User is suspended or closed | System returns HTTP 403. Generic error shown; form dismissed. |
| Unauthenticated request | System returns HTTP 401. Frontend redirects to `/login`. |

## Out of Scope

- Funding the account (top-up is a separate use case).
- Editing or closing an account.
- Account-level permissions or shared accounts.
- Non-cash asset types (stock holdings) — reserved for future iterations.
