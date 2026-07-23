# Use Case: View Transaction List

## Goal

An authenticated user navigates from their Accounts page to a dedicated transaction history page for a specific account, browses all ledger entries paginated 25 at a time, and can sort the visible page by any column.

## Actor

Authenticated User — a logged-in user with `active` status.

## Screen

- **Route:** `/accounts/{accountId}/transactions`
- **Page:** `TransactionListPage`
- **Entry point:** Authenticated user clicks the "Transactions" button on an account card on the Accounts page (`/accounts`).

## Trigger

Authenticated user clicks the "Transactions" button on an account card.

## Domain Models

- `domain/model/ledger-entry`
- `domain/model/account`
- `domain/model/session`

## Flows

- `domain/flows/view-transaction-list` (Flows A, B, C, D)

## Happy Path

1. Authenticated user is on `/accounts` and clicks "Transactions" on an account card.
2. Frontend navigates to `/accounts/{accountId}/transactions`.
3. Page renders the account name and currency as a heading and immediately fetches page 0 from `GET /api/v1/accounts/{accountId}/transactions?page=0&size=25`.
4. Backend validates ownership, queries the 25 most recent `LedgerEntry` rows for the account (ordered by `createdAt` descending), and returns them with `totalPages` and `totalCount`.
5. Table renders one row per entry. Columns: Direction, Asset Type, Value, Ticker, Shares, Description, Date. Cash entries leave Ticker and Shares blank.
6. Pagination controls appear below the table showing the current page and total pages.
7. User clicks a column header — the current page is sorted client-side by that column. A sort indicator appears. No new fetch is made.
8. User clicks "Next" — frontend fetches page 1 and replaces the table contents. Scrolls to top of table.
9. User continues navigating pages until done.

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| Account not found (HTTP 404) | Page shows "Account not found" error message. |
| Account not owned by user (HTTP 403) | Page shows a generic error message. |
| Transaction fetch fails (non-2xx) | Page shows error banner: "Could not load transactions." |
| No transactions | Table shows empty-state: "No transactions yet." Pagination hidden. |
| Unauthenticated request (HTTP 401) | Frontend redirects to `/login`. |

## Out of Scope

- Filtering transactions by type, date range, or asset type.
- Exporting transactions (CSV, PDF, etc.).
- Server-side sorting — all sorting is client-side on the current page only.
- Infinite scroll or load-more — navigation is strictly numbered pages.
- Editing or deleting transactions — `LedgerEntry` is immutable.
