# Tasks: View Transaction List

**Use case:** `domain/usecases/view-transaction-list.md`  
**Flow:** `domain/flows/view-transaction-list.md`  
**Issue:** #33

---

## DB Layer

### DB-1 — Update LedgerEntry entity: add ticker, shares, STOCK_BUY, STOCK_SELL

**Layer:** Database  
**Domain:** ledger  
**Use case:** view-transaction-list  
**Implements:** domain/model/ledger-entry — updated Properties and Business Rules  
**Inputs:**
- Existing `LedgerEntry` entity class at `org.dpp.tradelab.ledger.model.LedgerEntry`
- Updated `domain/model/ledger-entry.md`

**Outputs:**
- `LedgerEntry` entity with `assetType` enum extended to `CASH | STOCK_BUY | STOCK_SELL`
- Nullable `ticker: String?` column on entity
- Nullable `shares: BigDecimal?` column on entity (precision 19, scale 4)

**Acceptance criteria:**
- [ ] `LedgerEntryAssetType` enum contains exactly `CASH`, `STOCK_BUY`, `STOCK_SELL`; the old `STOCK` value is removed
- [ ] `ticker` is mapped with `@Column(nullable = true)`
- [ ] `shares` is mapped with `@Column(nullable = true, precision = 19, scale = 4)` and type `BigDecimal?`
- [ ] `@Enumerated(EnumType.STRING)` remains on `assetType`
- [ ] Entity still implements `Persistable<UUID>` and follows the `_isNew` pattern from standards
- [ ] `toString` updated to include `ticker` and `shares`
- [ ] Existing unit tests for the entity still compile and pass

**Depends on:** none

---

## REPO Layer

### REPO-1 — Add paginated LedgerEntry query by accountId

**Layer:** Repository  
**Domain:** ledger  
**Use case:** view-transaction-list  
**Implements:** view-transaction-list flow — Flow B, step 3  
**Inputs:**
- `accountId: UUID`
- `pageable: Pageable` (page index, page size=25, sort by `createdAt DESC`)

**Outputs:**
- `LedgerEntryRepository` interface with method `findByAccountId(accountId: UUID, pageable: Pageable): Page<LedgerEntry>`

**Acceptance criteria:**
- [ ] Method is declared on `LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>`
- [ ] Method name follows Spring Data JPA derived query conventions — no `@Query` annotation required
- [ ] Repository test using `@SpringBootTest` + `@AutoConfigureTestEntityManager` + `@Transactional` verifies entries are returned in `createdAt` descending order and page size is respected
- [ ] Test covers the empty-result case (zero entries for account)

**Depends on:** DB-1

---

## EXCEPTION Layer

### EXCEPTION-1 — AccountNotFoundException (ledger domain)

**Layer:** Exception  
**Domain:** ledger  
**Use case:** view-transaction-list  
**Implements:** view-transaction-list flow — Flow B error case "Account not found"  
**Inputs:** none (class definition only)  
**Outputs:** `AccountNotFoundException` class at `org.dpp.tradelab.ledger.exception.AccountNotFoundException`

**Acceptance criteria:**
- [ ] Class extends `RuntimeException`
- [ ] Constructor accepts a `UUID accountId` and produces the message `"Account not found: $accountId"`
- [ ] Class is in `org.dpp.tradelab.ledger.exception`
- [ ] `GlobalExceptionHandler` maps this exception to HTTP 404 with the standard `ErrorResponse` shape — add the mapping if not already present

**Depends on:** none

> **Note:** Check whether `AccountNotFoundException` already exists in the ledger domain from the open-account or top-up tasks. If it does and its contract matches, this task is a no-op — mark it done and skip.

---

### EXCEPTION-2 — AccountOwnershipException (ledger domain)

**Layer:** Exception  
**Domain:** ledger  
**Use case:** view-transaction-list  
**Implements:** view-transaction-list flow — Flow B error case "Account not owned by user"  
**Inputs:** none (class definition only)  
**Outputs:** `AccountOwnershipException` class at `org.dpp.tradelab.ledger.exception.AccountOwnershipException`

**Acceptance criteria:**
- [ ] Class extends `RuntimeException`
- [ ] Constructor accepts a `UUID accountId` and produces the message `"Account $accountId does not belong to the requesting user"`
- [ ] Class is in `org.dpp.tradelab.ledger.exception`
- [ ] `GlobalExceptionHandler` maps this exception to HTTP 403 with the standard `ErrorResponse` shape — add the mapping if not already present

**Depends on:** none

> **Note:** Check whether an equivalent ownership/forbidden exception already exists in the ledger domain. If so, reuse it and skip this task.

---

## SVC Layer

### SVC-1 — LedgerService.getTransactions: paginated ledger entry fetch with ownership check

**Layer:** Service  
**Domain:** ledger  
**Use case:** view-transaction-list  
**Implements:** view-transaction-list flow — Flow B, steps 2–4  
**Inputs:**
- `accountId: UUID` — the account whose transactions are being requested
- `userId: UUID` — the authenticated user's ID (from session, passed by controller)
- `page: Int` — 0-indexed page number
- `pageSize: Int` — always 25 (enforced by controller)

**Outputs:**
- `Page<LedgerEntry>` — Spring Data page containing up to 25 `LedgerEntry` rows ordered `createdAt DESC`, with `totalPages` and `totalElements`

**Acceptance criteria:**
- [ ] Method is annotated `@Transactional(readOnly = true)`
- [ ] Throws `AccountNotFoundException` if no `Account` with `accountId` exists
- [ ] Throws `AccountOwnershipException` if resolved account's `userId` does not equal the supplied `userId`
- [ ] Delegates to `LedgerEntryRepository.findByAccountId(accountId, pageable)` with a `PageRequest` of `page`, `pageSize`, and `Sort.by(Sort.Direction.DESC, "createdAt")`
- [ ] Returns the `Page<LedgerEntry>` unchanged to the caller
- [ ] Unit tests (KoTest + mockito-kotlin) cover: happy path returns correct page, `AccountNotFoundException` thrown when account missing, `AccountOwnershipException` thrown when user mismatch

**Depends on:** DB-1, REPO-1, EXCEPTION-1, EXCEPTION-2

---

## CONTROLLER Layer

### CONTROLLER-1 — LedgerApiDelegateImpl.getAccountTransactions

**Layer:** Controller  
**Domain:** ledger  
**Use case:** view-transaction-list  
**Implements:** view-transaction-list flow — Flow B, steps 1 and 4  
**Inputs:**
- `accountId: UUID` (path parameter)
- `userId: UUID` (query parameter — the authenticated user's ID)
- `page: Int` (query parameter, default 0)
- Generated `LedgerApiDelegate` interface method `getAccountTransactions`

**Outputs:**
- `ResponseEntity<TransactionListResponse>` — HTTP 200 with body containing `transactions` (list of `TransactionResponse`), `page`, `totalPages`, `totalCount`
- HTTP 404 via `AccountNotFoundException` → `GlobalExceptionHandler`
- HTTP 403 via `AccountOwnershipException` → `GlobalExceptionHandler`
- HTTP 401 if no valid session (handled by existing auth mechanism)

**Acceptance criteria:**
- [ ] Delegate method calls `ledgerService.getTransactions(accountId, userId, page, pageSize=25)`
- [ ] Maps each `LedgerEntry` in the returned `Page` to a `TransactionResponse` DTO (generated): `id`, `type`, `assetType`, `amount`, `currency`, `ticker` (nullable), `shares` (nullable), `description` (nullable), `createdAt`
- [ ] Sets `page`, `totalPages` (from `Page.totalPages`), `totalCount` (from `Page.totalElements`) in the response
- [ ] Returns `ResponseEntity.ok(body)`
- [ ] `@SpringBootTest` + MockMvc tests cover: 200 happy path with correct body shape, 404 when account missing, 403 when ownership mismatch

**Depends on:** SVC-1, API-CONTRACT-1

---

## API-CONTRACT Layer

### API-CONTRACT-1 — Append GET /accounts/{accountId}/transactions to ledger-openapi.yaml

**Layer:** OpenAPI Contract  
**Domain:** ledger  
**Use case:** view-transaction-list  
**Implements:** view-transaction-list flow — Flow B, step 1 (request) and step 4 (response shape)  
**Inputs:**
- Existing `services/contract/ledger-openapi.yaml`
- New path: `GET /accounts/{accountId}/transactions`
- Path parameter: `accountId` (UUID, required)
- Query parameters: `userId` (UUID, required), `page` (integer, optional, default 0, minimum 0)
- Response 200 schema: `TransactionListResponse` — `{ transactions: TransactionResponse[], page: integer, totalPages: integer, totalCount: integer }`
- `TransactionResponse` schema: `{ id: uuid, type: CREDIT|DEBIT, assetType: CASH|STOCK_BUY|STOCK_SELL, amount: number, currency: string, ticker: string (nullable), shares: number (nullable), description: string (nullable), createdAt: date-time }`
- Error responses: 401, 403, 404 (reuse existing `ErrorResponse` schema)

**Outputs:**
- Updated `services/contract/ledger-openapi.yaml` with the new path, `TransactionListResponse`, and `TransactionResponse` schemas appended under `components/schemas`

**Acceptance criteria:**
- [ ] `operationId` is `getAccountTransactions`
- [ ] `accountId` is a required path parameter of type `string` / `format: uuid`
- [ ] `userId` is a required query parameter of type `string` / `format: uuid`
- [ ] `page` is an optional query parameter, type `integer`, default `0`, minimum `0`
- [ ] Response 200 references `TransactionListResponse`
- [ ] `TransactionListResponse` has required fields: `transactions`, `page`, `totalPages`, `totalCount`
- [ ] `TransactionResponse` has required fields: `id`, `type`, `assetType`, `amount`, `currency`, `createdAt`; optional fields: `ticker`, `shares`, `description`
- [ ] `assetType` enum in `TransactionResponse` lists exactly `CASH`, `STOCK_BUY`, `STOCK_SELL`
- [ ] Error responses 401, 403, 404 reference existing `ErrorResponse` schema
- [ ] Existing paths (`/accounts`, `/accounts/{accountId}/top-up`) are unchanged
- [ ] YAML is valid OpenAPI 3.0.3

**Depends on:** none

---

## CLI Layer

### CLI-1 — fetchTransactions API client function

**Layer:** API Client  
**Domain:** ledger  
**Use case:** view-transaction-list  
**Implements:** view-transaction-list flow — Flow B step 1, Flow C step 2  
**Inputs:**
- `accountId: string` (UUID)
- `userId: string` (UUID)
- `page: number` (0-indexed)
- Shared Axios instance from `shared/api/`
- `ledger-openapi.yaml` contract for URL, method, params, and response shape

**Outputs:**
- Function `fetchTransactions(accountId, userId, page): Promise<TransactionListResponse>` in `services/front-end/src/domains/ledger/api/transactionApi.ts`
- TypeScript interfaces `TransactionResponse` and `TransactionListResponse` in `services/front-end/src/domains/ledger/types/transaction.ts`
- `TRANSACTION_KEYS` cache key constant exported from `transactionApi.ts`

**Acceptance criteria:**
- [ ] Calls `GET /api/v1/accounts/{accountId}/transactions?userId={userId}&page={page}&size=25` using the shared Axios instance
- [ ] `size` is always hardcoded to `25` — it is not a function parameter
- [ ] Response is typed as `TransactionListResponse`
- [ ] `TransactionResponse` interface includes: `id: string`, `type: 'CREDIT' | 'DEBIT'`, `assetType: 'CASH' | 'STOCK_BUY' | 'STOCK_SELL'`, `amount: number`, `currency: string`, `ticker: string | null`, `shares: number | null`, `description: string | null`, `createdAt: string`
- [ ] `TransactionListResponse` interface includes: `transactions: TransactionResponse[]`, `page: number`, `totalPages: number`, `totalCount: number`
- [ ] No `any` types
- [ ] Unit test mocks Axios and asserts the correct URL, params, and return type shape

**Depends on:** API-CONTRACT-1

---

## STATE Layer

### STATE-1 — useTransactions TanStack Query hook

**Layer:** State  
**Domain:** ledger  
**Use case:** view-transaction-list  
**Implements:** view-transaction-list flow — Flow B (data fetching), Flow C (page change triggers re-fetch)  
**Inputs:**
- `accountId: string`
- `userId: string`
- `page: number` (controlled by the page component)
- `fetchTransactions` from CLI-1

**Outputs:**
- Hook `useTransactions(accountId, userId, page)` in `services/front-end/src/domains/ledger/hooks/useTransactions.ts`
- Returns: `{ data: TransactionListResponse | undefined, isLoading: boolean, isError: boolean, error: unknown }`

**Acceptance criteria:**
- [ ] Uses `useQuery` from TanStack Query v5
- [ ] Query key includes `TRANSACTION_KEYS`, `accountId`, `userId`, and `page` — changing any of them triggers a fresh fetch
- [ ] `staleTime` is `0` — always fetches on mount and on page change
- [ ] On error (non-2xx), `isError` is `true` and `error` is populated
- [ ] Hook test using `renderHook` covers: loading state, successful data return, error state

**Depends on:** CLI-1

---

## COMP Layer

### COMP-1 — TransactionTable component

**Layer:** Component  
**Domain:** ledger  
**Use case:** view-transaction-list  
**Implements:** view-transaction-list flow — Flow B steps 5–6, Flow D steps 1–3  
**Inputs:**
- `transactions: TransactionResponse[]`
- `isLoading: boolean`
- `isError: boolean`

**Outputs:**
- Component `TransactionTable` in `services/front-end/src/domains/ledger/components/TransactionTable.tsx`
- Props interface `TransactionTableProps`

**Acceptance criteria:**
- [ ] Renders a table with columns: Direction, Asset Type, Value, Ticker, Shares, Description, Date
- [ ] `Direction` displays `CREDIT` or `DEBIT`
- [ ] `Asset Type` displays `CASH`, `STOCK_BUY`, or `STOCK_SELL`
- [ ] `Value` displays `amount` formatted to 2 decimal places followed by `currency` (e.g. `1,000.00 USD`)
- [ ] `Ticker` cell is empty when `ticker` is `null`
- [ ] `Shares` cell is empty when `shares` is `null`
- [ ] `Description` cell is empty when `description` is `null`
- [ ] `Date` displays `createdAt` converted from UTC to local timezone
- [ ] Clicking a column header cycles sort state: ascending → descending → unsorted. A visible sort indicator (↑ / ↓) appears on the active column header
- [ ] Default sort on mount is `createdAt` descending (most recent first), matching the server order
- [ ] Sorting is applied entirely client-side — no external callback is invoked on sort change
- [ ] When `isLoading` is `true`, a loading skeleton or spinner is displayed instead of rows
- [ ] When `isError` is `true`, an error banner "Could not load transactions." is displayed
- [ ] When `transactions` is an empty array, the message "No transactions yet." is shown and no table header is rendered
- [ ] Component tests cover: happy path with cash row (Ticker/Shares blank), happy path with stock row (Ticker and Shares visible), empty state, loading state, error state, column sort cycling

**Depends on:** CLI-1 (for `TransactionResponse` type)

---

### COMP-2 — PaginationControls component

**Layer:** Component  
**Domain:** ledger  
**Use case:** view-transaction-list  
**Implements:** view-transaction-list flow — Flow B step 6, Flow C step 1  
**Inputs:**
- `currentPage: number` (0-indexed)
- `totalPages: number`
- `onPageChange: (page: number) => void`

**Outputs:**
- Component `PaginationControls` in `services/front-end/src/domains/ledger/components/PaginationControls.tsx`
- Props interface `PaginationControlsProps`

**Acceptance criteria:**
- [ ] Renders "Previous" and "Next" buttons
- [ ] "Previous" is disabled when `currentPage === 0`
- [ ] "Next" is disabled when `currentPage === totalPages - 1`
- [ ] Displays current page as 1-indexed (`currentPage + 1`) and `totalPages` (e.g. "Page 2 of 5")
- [ ] Clicking "Previous" calls `onPageChange(currentPage - 1)`
- [ ] Clicking "Next" calls `onPageChange(currentPage + 1)`
- [ ] When `totalPages === 0`, component renders nothing
- [ ] Component tests cover: first page (Previous disabled), last page (Next disabled), middle page (both enabled), zero total pages (renders nothing), Previous/Next click callbacks fire with correct page numbers

**Depends on:** none

---

## SCREEN Layer

### SCREEN-1 — TransactionListPage

**Layer:** Screen  
**Domain:** ledger  
**Use case:** view-transaction-list  
**Implements:** view-transaction-list flow — Flow A steps 2–3, Flow B (full), Flow C (full)  
**Inputs:**
- `accountId` from React Router route param (`/accounts/:accountId/transactions`)
- `userId` from Zustand session slice
- `accountName` and `currency` from React Router location state (passed by the Accounts page on navigation)
- `useTransactions` hook (STATE-1)
- `TransactionTable` component (COMP-1)
- `PaginationControls` component (COMP-2)

**Outputs:**
- Page component `TransactionListPage` in `services/front-end/src/domains/ledger/pages/TransactionListPage.tsx`
- Route `/accounts/:accountId/transactions` registered in `app/router.tsx`

**Acceptance criteria:**
- [ ] Reads `accountId` from route params (via `useParams`) and `userId` from the Zustand session store
- [ ] Reads `accountName` and `currency` from `useLocation().state`; falls back to displaying `accountId` in the heading if state is absent
- [ ] Manages `page` state starting at `0`; passes it to `useTransactions`
- [ ] Page heading displays account name and currency (e.g. "My Trading Account — USD")
- [ ] Renders `TransactionTable` with `transactions`, `isLoading`, `isError` from the hook
- [ ] Renders `PaginationControls` with `currentPage`, `totalPages` from the hook data, and `onPageChange`
- [ ] `onPageChange` updates `page` state (triggering a new fetch) and scrolls the table container to the top
- [ ] Redirects to `/login` when `userId` is absent from the session store (unauthenticated)
- [ ] Page tests cover: renders heading and table on success, previous/next page change triggers re-fetch with updated page, redirects to `/login` when no session

**Depends on:** STATE-1, COMP-1, COMP-2

---

### SCREEN-2 — Add "Transactions" button to account cards on AccountsPage

**Layer:** Screen  
**Domain:** ledger  
**Use case:** view-transaction-list  
**Implements:** view-transaction-list flow — Flow A, step 1  
**Inputs:**
- Existing `AccountCard` component or account list rendering inside `AccountsPage`
- `account.id: string`, `account.name: string`, `account.currency: string`
- React Router `useNavigate`

**Outputs:**
- "Transactions" button added to each account card on the Accounts page (`/accounts`)
- On click, navigates to `/accounts/{accountId}/transactions` with location state `{ accountName: account.name, currency: account.currency }`

**Acceptance criteria:**
- [ ] Each account card renders a "Transactions" button alongside the existing "Top Up" button
- [ ] Clicking navigates to `/accounts/${account.id}/transactions` with state `{ accountName: account.name, currency: account.currency }`
- [ ] No business logic or API call is made on this click — navigation only
- [ ] Test covers: button renders for each account, click triggers `navigate` with correct path and state

**Depends on:** SCREEN-1

---

## Dependency summary

| Task ID | Title | Depends on |
|---|---|---|
| DB-1 | Update LedgerEntry entity | none |
| REPO-1 | Paginated LedgerEntry query | DB-1 |
| EXCEPTION-1 | AccountNotFoundException (ledger) | none |
| EXCEPTION-2 | AccountOwnershipException (ledger) | none |
| SVC-1 | LedgerService.getTransactions | DB-1, REPO-1, EXCEPTION-1, EXCEPTION-2 |
| API-CONTRACT-1 | Append GET transactions to ledger-openapi.yaml | none |
| CONTROLLER-1 | LedgerApiDelegateImpl.getAccountTransactions | SVC-1, API-CONTRACT-1 |
| CLI-1 | fetchTransactions API client | API-CONTRACT-1 |
| STATE-1 | useTransactions hook | CLI-1 |
| COMP-1 | TransactionTable component | CLI-1 (types) |
| COMP-2 | PaginationControls component | none |
| SCREEN-1 | TransactionListPage | STATE-1, COMP-1, COMP-2 |
| SCREEN-2 | Transactions button on AccountCard | SCREEN-1 |
