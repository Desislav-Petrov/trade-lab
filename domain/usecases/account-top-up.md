# Use Case: Account Top-Up

## Goal

An authenticated user adds simulated funds to one of their paper trading accounts so they have a cash balance available for trading.

## Actor

Authenticated User — a logged-in user with `active` status.

## Screen

- **Route:** `/accounts`
- **Page:** `AccountsPage`
- **Entry point:** Authenticated user navigates to the Accounts page via the sidebar and clicks "Top Up" on a specific account.

## Trigger

Authenticated user clicks the "Top Up" button on an account card and submits the top-up modal form.

## Domain Models

- `domain/model/account`
- `domain/model/ledger-entry`
- `domain/model/user`

## Flows

- `domain/flows/account-top-up`
- `domain/flows/user-session` (Flow A — Session UI Display, for the Accounts sidebar link)

## Happy Path

1. Authenticated user navigates to `/accounts` — the page performs a fresh fetch of all accounts from the backend and renders them.
2. User clicks "Top Up" on a specific account card.
3. A modal opens showing the account name, currency, and a single "Amount" input field.
4. User enters a valid whole number between 1 and 10,000,000.
5. Frontend validates the input client-side (whole number, within range) before enabling the Confirm button.
6. User clicks "Confirm". Frontend shows a loading state.
7. System validates the request, updates `Account.balance` by adding the top-up amount, and appends a `CREDIT` `LedgerEntry`.
8. System emits `AccountToppedUp` and returns HTTP 200 with the new balance.
9. Modal displays a green tick and "Top up successful".
10. Frontend re-fetches all accounts from the backend and updates the Accounts page with the latest balances. Modal is dismissed.

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| Amount is zero or negative | Inline field error: "Amount must be at least 1." Confirm button blocked. |
| Amount exceeds 10,000,000 | Inline field error: "Amount must not exceed 10,000,000." Confirm button blocked. |
| Amount is not a whole number | Inline field error: "Amount must be a whole number." Confirm button blocked. |
| Account not found (HTTP 404) | Modal shows a generic error message. |
| Account not owned by user (HTTP 403) | Modal shows a generic error message. |
| Account not active (HTTP 403) | Modal shows a generic error message. |
| Unauthenticated request (HTTP 401) | Frontend redirects to `/login`. |

## Out of Scope

- FX conversion — top-up currency always matches the account's base currency.
- Topping up multiple accounts in a single operation.
- Scheduled or recurring top-ups.
- Withdrawals or debiting cash out of an account.
- Non-cash asset top-ups (stock positions).
- Top-up history view (covered by the ledger).
