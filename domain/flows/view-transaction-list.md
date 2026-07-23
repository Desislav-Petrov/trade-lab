# View Transaction List

## Overview

Allows an authenticated user to view the full transaction history for a specific account on a dedicated page (`/accounts/{accountId}/transactions`). The list is paginated (25 entries per page), sorted reverse-chronologically by default, and sortable client-side by any visible column. Each row represents one immutable `LedgerEntry`. Cash entries show direction, value, date, and purpose. Stock entries additionally show the ticker symbol and share count.

## Actors

- **Authenticated User**: A logged-in user who wishes to inspect the transaction history of one of their accounts.
- **Guest Browser**: The React frontend rendering the transaction list page.
- **System**: The platform backend responsible for fetching and paginating ledger entries.

## Preconditions

- The user has an active session (is logged in).
- The user's `status` is `active`.
- The target account exists and belongs to the authenticated user.

## Steps

### Flow A — Navigate to Transaction List

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click "Transactions" on an account | Clicks the Transactions button displayed alongside the target account on the Accounts page (`/accounts`). |
| 2 | Guest Browser | Navigate to transaction page | Routes to `/accounts/{accountId}/transactions`. |
| 3 | Guest Browser | Render transaction list page | Displays the account name and currency as a page heading. Fetches the first page of transactions immediately on mount. |

### Flow B — Load Transaction Page

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest Browser | Fetch page of transactions | Calls `GET /api/v1/accounts/{accountId}/transactions?page={page}&size=25`. `page` is 0-indexed. Default on mount is `page=0`. |
| 2 | System | Validate request | Checks: `accountId` resolves to an account that belongs to the authenticated user. |
| 3 | System | Query ledger entries | Retrieves entries for the account ordered by `createdAt` descending, offset by `page * 25`, limit 25. Returns the page of results and the total entry count. |
| 4 | System | Return HTTP 200 | Response body includes: `transactions` (array), `page` (current page number), `totalPages`, `totalCount`. Each transaction item includes: `id`, `type` (`CREDIT`/`DEBIT`), `assetType`, `amount`, `currency`, `ticker` (nullable), `shares` (nullable), `description` (nullable), `createdAt`. |
| 5 | Guest Browser | Render table | Displays one row per transaction. Columns: **Direction** (`CREDIT`/`DEBIT`), **Asset Type**, **Value** (amount + currency), **Ticker** (blank for cash entries), **Shares** (blank for cash entries), **Description**, **Date**. Default sort: `createdAt` descending (most recent first). |
| 6 | Guest Browser | Render pagination controls | Displays page controls showing the current page, total pages, and previous/next (and first/last) navigation. Disables previous on page 1; disables next on last page. |

### Flow C — Change Page

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click a page control | Clicks a page number, "Previous", or "Next" in the pagination controls. |
| 2 | Guest Browser | Fetch requested page | Calls `GET /api/v1/accounts/{accountId}/transactions?page={n}&size=25`. Replaces the current table contents with the new page. Scrolls to the top of the table. |
| 3 | Guest Browser | Update pagination controls | Reflects the new current page. |

### Flow D — Sort Transactions

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click a column header | Clicks any column header in the transaction table. |
| 2 | Guest Browser | Apply client-side sort | Sorts the currently loaded page by the selected column. Cycles through: ascending → descending → unsorted (original server order) on repeated clicks. No new API call is made. |
| 3 | Guest Browser | Update column header | Displays a sort indicator (arrow) on the active column reflecting the current sort direction. |

## Postconditions

- The transaction list page displays up to 25 `LedgerEntry` rows for the selected account.
- The default view is reverse-chronological (most recent first).
- Pagination controls allow navigation across all pages without loading all entries at once.
- Client-side sort can reorder any column on the current page without an additional fetch.

## Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Account not found | `accountId` does not resolve to an account | System returns HTTP 404. Page shows a "Account not found" error message. |
| Account not owned by user | Resolved account's `userId` does not match the session user | System returns HTTP 403. Page shows a generic error message. |
| Fetch fails | `GET /api/v1/accounts/{accountId}/transactions` returns a non-2xx response | Page shows an error banner: "Could not load transactions." Retry is possible by refreshing the page. |
| No transactions | Account has zero `LedgerEntry` rows | Table shows an empty-state message: "No transactions yet." Pagination controls are hidden. |
| Unauthenticated request | No valid session | System returns HTTP 401. Frontend redirects to `/login`. |

## Domain Models Involved

- **LedgerEntry**: Read at Flow B step 3 — paginated, ordered by `createdAt` descending. All fields are returned.
- **Account**: Read at Flow B step 2 — validated for existence and ownership. `name` and `currency` are used in the page heading.
- **Session**: Read at Flow B step 1 — `userId` sourced from the Zustand session slice to validate account ownership.
