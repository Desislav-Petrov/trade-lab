# Tasks: Select Trading Account (issue #38)

## Files Read During Startup

- `AGENTS.md`
- `domain/model/account.md`
- `domain/model/ledger-entry.md`
- `domain/model/user.md`
- `domain/model/session.md`
- `domain/model/asset-subscription.md`
- `domain/model/market-data-snapshot.md`
- `domain/flows/select-trading-account.md`
- `domain/flows/open-account.md`
- `domain/flows/account-top-up.md`
- `domain/flows/manage-asset-subscriptions.md`
- `domain/flows/market-data-websocket-feed.md`
- `domain/usecases/trade-stock-page.md`
- `domain/usecases/open-account.md`
- `standards/architecture.md`
- `standards/backend.md`
- `standards/frontend.md`
- `services/contract/ledger-openapi.yaml`
- `services/backend/src/main/kotlin/org/dpp/tradelab/ledger/repository/AccountRepository.kt`
- `services/backend/src/main/kotlin/org/dpp/tradelab/ledger/service/AccountService.kt`
- `services/backend/src/main/kotlin/org/dpp/tradelab/ledger/controller/LedgerApiDelegateImpl.kt`
- `services/front-end/src/domains/ledger/api/accountApi.ts`
- `services/front-end/src/domains/ledger/hooks/useLedger.ts`
- `services/front-end/src/domains/ledger/types/account.ts`
- `services/front-end/src/domains/stocktrading/pages/StockTradingPage.tsx`
- `decisions/2026-07-07-selected-account-in-stocktrading-slice.md`

---

## Use Case Summary

An authenticated user on `/trade` selects which active paper trading account will fund their stock purchases. On page mount the frontend fetches active accounts via `GET /api/v1/accounts?userId={userId}&status=active` and stores the first account's `id` in the `stocktrading` Zustand slice as a default if no selection is already held. The user can change the selection at any time; the choice persists for the session. No backend business logic changes — this is an additive query-param filter on the existing list endpoint. No funds are moved; the selector is preparatory for a future buy flow.

---

## Ambiguities and Gaps

None. The flow, model, existing backend code, and existing frontend code are all consistent with one another. The one notable finding is that the existing `GET /accounts` endpoint does not accept a `status` query parameter — this must be added across REPO, SVC, CONTROLLER, and API-CONTRACT layers before the frontend can filter server-side.

---

## Layer: REPO

### REPO-1 — Add `findAllByUserIdAndStatus` to `AccountRepository`

**Layer:** Repository  
**Domain:** ledger  
**Use case:** select-trading-account  
**Implements:** select-trading-account Flow A — step 3 (backend returns accounts filtered to `status: active`)  
**Inputs:**
- `userId: UUID`
- `status: AccountStatus` (enum, existing)

**Outputs:**
- New derived query method `findAllByUserIdAndStatus(userId: UUID, status: AccountStatus): List<Account>` on `AccountRepository`

**Acceptance criteria:**
- [ ] `AccountRepository` has `fun findAllByUserIdAndStatus(userId: UUID, status: AccountStatus): List<Account>` — Spring Data JPA derives the query; no `@Query` annotation needed.
- [ ] Existing `findAllByUserId` method is untouched.
- [ ] Unit test (or repository integration test) covers: returns only accounts with the given status for the given user; returns empty list when no match.

**Depends on:** none

---

## Layer: SVC

### SVC-1 — Add `listActiveAccountsByUser` to `AccountService`

**Layer:** Service  
**Domain:** ledger  
**Use case:** select-trading-account  
**Implements:** select-trading-account Flow A — step 3  
**Inputs:**
- `userId: UUID`

**Outputs:**
- New method `listActiveAccountsByUser(userId: UUID): List<Account>` on `AccountService`
- Delegates to `accountRepository.findAllByUserIdAndStatus(userId, AccountStatus.ACTIVE)`
- Returns results ordered by `createdAt` ascending (use `sortedBy { it.createdAt }` in the service layer — no custom repo sort needed)

**Acceptance criteria:**
- [ ] Method is annotated `@Transactional(readOnly = true)`.
- [ ] Delegates exclusively to the repository — no business logic beyond the sort.
- [ ] Existing `listAccountsByUser` method is untouched.
- [ ] Unit test covers: happy path returns only active accounts sorted by `createdAt` ascending; empty list when no active accounts exist.

**Depends on:** REPO-1

---

## Layer: CONTROLLER

### CONTROLLER-1 — Extend `listAccounts` to accept optional `status` query param

**Layer:** Controller  
**Domain:** ledger  
**Use case:** select-trading-account  
**Implements:** select-trading-account Flow A — step 3  
**Inputs:**
- `userId: UUID` (existing, required)
- `status: String?` (new, optional query param — valid values: `ACTIVE`, `SUSPENDED`, `CLOSED`)

**Outputs:**
- Updated `listAccounts` override in `LedgerApiDelegateImpl` that:
  - When `status` is `null` or absent: calls existing `accountService.listAccountsByUser(userId)` (unchanged behaviour)
  - When `status` is `"ACTIVE"`: calls `accountService.listActiveAccountsByUser(userId)`
  - When `status` is any other non-null value: returns HTTP 400 with a standard error response
- Returns `ResponseEntity<AccountListResponse>` in all success cases

**Acceptance criteria:**
- [ ] `GET /api/v1/accounts?userId=...` (no status param) still returns all accounts — existing behaviour preserved.
- [ ] `GET /api/v1/accounts?userId=...&status=ACTIVE` returns only active accounts, ordered by `createdAt` ascending.
- [ ] `GET /api/v1/accounts?userId=...&status=INVALID` returns HTTP 400 with a standard `ErrorResponse` body.
- [ ] MockMvc tests cover all three cases above.
- [ ] No business logic in the controller — service calls only.

**Depends on:** SVC-1

---

## Layer: API-CONTRACT

### API-CONTRACT-1 — Add `status` query parameter to `GET /accounts` in `ledger-openapi.yaml`

**Layer:** OpenAPI Contract  
**Domain:** ledger  
**Use case:** select-trading-account  
**Implements:** select-trading-account Flow A — step 2 and step 3  
**Inputs:**
- Existing `GET /accounts` path in `services/contract/ledger-openapi.yaml`
- New optional query parameter: `status` — type `string`, enum `[ACTIVE, SUSPENDED, CLOSED]`, not required

**Outputs:**
- Updated `services/contract/ledger-openapi.yaml` with `status` added as an optional query parameter to `GET /accounts`
- No new paths, no new schemas, no removals

**Acceptance criteria:**
- [ ] `GET /accounts` in the YAML now has two parameters: `userId` (existing, required) and `status` (new, optional, enum `[ACTIVE, SUSPENDED, CLOSED]`).
- [ ] All existing responses (`200`, `401`) are unchanged.
- [ ] `info.title` remains `Trade Lab API — Ledger`.
- [ ] YAML is valid OpenAPI 3.0.3.

**Depends on:** CONTROLLER-1

---

## Layer: CLI

### CLI-1 — Add `status` filter param to `fetchAccounts` in `accountApi.ts`

**Layer:** API Client  
**Domain:** ledger  
**Use case:** select-trading-account  
**Implements:** select-trading-account Flow A — step 2  
**Inputs:**
- `userId: string` (existing)
- `status?: 'ACTIVE' | 'SUSPENDED' | 'CLOSED'` (new, optional)

**Outputs:**
- Updated `fetchAccounts` function signature in `services/front-end/src/domains/ledger/api/accountApi.ts`:
  ```ts
  fetchAccounts(userId: string, status?: 'ACTIVE' | 'SUSPENDED' | 'CLOSED'): Promise<AccountListResponse>
  ```
- When `status` is provided, it is passed as a query param `status=<value>`.
- When `status` is omitted, behaviour is identical to today (no status param sent).

**Acceptance criteria:**
- [ ] `fetchAccounts('uuid')` makes `GET /v1/accounts?userId=uuid` — no status param.
- [ ] `fetchAccounts('uuid', 'ACTIVE')` makes `GET /v1/accounts?userId=uuid&status=ACTIVE`.
- [ ] Existing callers of `fetchAccounts` that pass only `userId` continue to compile and work.
- [ ] Unit tests cover both call variants.

**Depends on:** none (can be implemented from the contract; does not depend on API-CONTRACT-1 at build time)

---

## Layer: STATE

### STATE-1 — Add `selectedAccountId` slice to `stocktrading` Zustand store

**Layer:** State  
**Domain:** stocktrading  
**Use case:** select-trading-account  
**Implements:** select-trading-account Flow B — step 3; Flow A — step 4  
**Inputs:** none (new file)

**Outputs:**
- New file `services/front-end/src/domains/stocktrading/hooks/useStockTradingStore.ts`
- Zustand store slice with:
  - `selectedAccountId: string | null` — initially `null`
  - `setSelectedAccountId(id: string): void` — sets the value
  - `clearSelectedAccountId(): void` — resets to `null`

**Acceptance criteria:**
- [ ] `useStockTradingStore` is a Zustand store created with `create<...>()`.
- [ ] Initial state has `selectedAccountId: null`.
- [ ] `setSelectedAccountId('some-uuid')` updates `selectedAccountId` to `'some-uuid'`.
- [ ] `clearSelectedAccountId()` resets `selectedAccountId` to `null`.
- [ ] Unit tests cover initial state, `setSelectedAccountId`, and `clearSelectedAccountId`.

**Depends on:** none

---

### STATE-2 — Add `useActiveAccounts` hook to `ledger/hooks/useLedger.ts`

**Layer:** State  
**Domain:** ledger  
**Use case:** select-trading-account  
**Implements:** select-trading-account Flow A — steps 2–4  
**Inputs:**
- Reads `userId` from the session Zustand store (same pattern as existing `useAccounts`)

**Outputs:**
- New exported hook `useActiveAccounts()` added to `services/front-end/src/domains/ledger/hooks/useLedger.ts`
- Uses TanStack Query `useQuery` with:
  - `queryKey: [ACCOUNTS_QUERY_KEY, userId, 'ACTIVE']`
  - `queryFn`: calls `fetchAccounts(userId!, 'ACTIVE')`
  - `enabled: !!userId`
  - `staleTime: 0` (re-fetch on every mount)
- Returns `{ data, isLoading, isError }` — standard `useQuery` return

**Acceptance criteria:**
- [ ] Hook calls `fetchAccounts` with `status: 'ACTIVE'` when `userId` is present.
- [ ] Hook does not fire when `userId` is absent (`enabled: false`).
- [ ] `staleTime` is exactly `0`.
- [ ] Query key includes `'ACTIVE'` as the third element so it does not share a cache entry with `useAccounts`.
- [ ] Unit tests cover: fetches active accounts when userId present; does not fetch when userId absent; returns error state on API failure.

**Depends on:** CLI-1

---

## Layer: COMP

### COMP-1 — Create `AccountSelector` component

**Layer:** Component  
**Domain:** stocktrading  
**Use case:** select-trading-account  
**Implements:** select-trading-account Flow A — step 5; Flow B — steps 1–4  
**Inputs (props):**
- `accounts: AccountResponse[]` — list of active accounts to display
- `selectedAccountId: string | null` — currently selected account id
- `onSelect: (accountId: string) => void` — called when user picks an account
- `isLoading: boolean` — shows a loading state while accounts are being fetched
- `isError: boolean` — shows an error state when the fetch failed

**Outputs:**
- New file `services/front-end/src/domains/stocktrading/components/AccountSelector.tsx`
- New test file `services/front-end/src/domains/stocktrading/components/AccountSelector.test.tsx`

**Rendering rules:**
- While `isLoading` is true: render a loading indicator (text or spinner, not a skeleton).
- When `isError` is true: render `<p role="alert">Could not load accounts.</p>`.
- When `accounts` is empty and not loading/error: render `<p>No accounts available. Open an account first.</p>`.
- When `accounts` is non-empty: render a `<select>` element. Each `<option>` displays `{account.name} ({account.currency})` and has `value={account.id}`. The `<select>` value is `selectedAccountId ?? ''`. On change, call `onSelect` with the chosen account id.

**Acceptance criteria:**
- [ ] Renders loading indicator when `isLoading` is true.
- [ ] Renders `role="alert"` error message when `isError` is true.
- [ ] Renders empty-state paragraph when `accounts` is empty.
- [ ] Renders `<select>` with one `<option>` per account showing `name (currency)`.
- [ ] Selecting a different option calls `onSelect` with the correct account id.
- [ ] The `<select>` reflects `selectedAccountId` as its current value.
- [ ] Props interface is named `AccountSelectorProps`.
- [ ] No API calls or store access inside the component.
- [ ] Tests cover all rendering states and the `onSelect` callback.

**Depends on:** none (pure presentational component)

---

## Layer: SCREEN

### SCREEN-1 — Integrate `AccountSelector` into `StockTradingPage`

**Layer:** Screen  
**Domain:** stocktrading  
**Use case:** select-trading-account  
**Implements:** select-trading-account Flow A — steps 1–5; Flow B — steps 1–4  
**Inputs:**
- Existing `StockTradingPage.tsx`
- `useActiveAccounts` hook from `ledger/hooks/useLedger.ts`
- `useStockTradingStore` from `stocktrading/hooks/useStockTradingStore.ts`
- `AccountSelector` component

**Outputs:**
- Updated `services/front-end/src/domains/stocktrading/pages/StockTradingPage.tsx`
- Updated `services/front-end/src/domains/stocktrading/pages/StockTradingPage.test.tsx`

**Wiring logic (no business logic — assembly only):**
1. Call `useActiveAccounts()` — destructure `data`, `isLoading`, `isError`.
2. Read `selectedAccountId` and `setSelectedAccountId` from `useStockTradingStore`.
3. In a `useEffect` that depends on `data`: if `selectedAccountId` is `null` and `data?.accounts` has at least one entry, call `setSelectedAccountId(data.accounts[0].id)`.
4. Render `<AccountSelector>` above the existing market data grid, passing the four required props.

**Acceptance criteria:**
- [ ] `AccountSelector` is rendered on the page.
- [ ] On mount with accounts returned and no prior selection, `selectedAccountId` is set to the first account's `id`.
- [ ] On mount with accounts returned and a prior selection, `selectedAccountId` is not changed.
- [ ] On mount with no accounts returned, `selectedAccountId` remains `null`.
- [ ] Selecting a different account in the `AccountSelector` updates `selectedAccountId` in the store.
- [ ] `isLoading` and `isError` states from `useActiveAccounts` are forwarded to `AccountSelector`.
- [ ] All pre-existing functionality (subscription list, market data grid, add/remove ticker panels) is unaffected.
- [ ] `StockTradingPage.test.tsx` is updated to cover the four account-selector scenarios above.

**Depends on:** STATE-1, STATE-2, COMP-1

---

## Dependency Summary

| Task ID | Title | Depends On |
|---|---|---|
| REPO-1 | Add `findAllByUserIdAndStatus` to `AccountRepository` | none |
| SVC-1 | Add `listActiveAccountsByUser` to `AccountService` | REPO-1 |
| CONTROLLER-1 | Extend `listAccounts` with optional `status` param | SVC-1 |
| API-CONTRACT-1 | Add `status` param to `GET /accounts` in `ledger-openapi.yaml` | CONTROLLER-1 |
| CLI-1 | Add `status` filter param to `fetchAccounts` | none |
| STATE-1 | Add `selectedAccountId` slice to stocktrading Zustand store | none |
| STATE-2 | Add `useActiveAccounts` hook to `useLedger.ts` | CLI-1 |
| COMP-1 | Create `AccountSelector` component | none |
| SCREEN-1 | Integrate `AccountSelector` into `StockTradingPage` | STATE-1, STATE-2, COMP-1 |
