# Tasks: Account Top-Up

**Use case:** `domain/usecases/account-top-up.md`
**Flow:** `domain/flows/account-top-up.md`
**Domain:** ledger

---

## Files read during decomposition

| File | Status |
|---|---|
| `AGENTS.md` | ✅ |
| `domain/model/account.md` | ✅ |
| `domain/model/ledger-entry.md` | ✅ |
| `domain/model/user.md` | ✅ |
| `domain/model/session.md` | ✅ |
| `domain/flows/account-top-up.md` | ✅ |
| `domain/flows/open-account.md` | ✅ |
| `domain/flows/user-session.md` | ✅ |
| `domain/usecases/account-top-up.md` | ✅ |
| `standards/backend.md` | ✅ |
| `standards/frontend.md` | ✅ |
| `standards/architecture.md` | ✅ |
| `services/contract/ledger-openapi.yaml` | ✅ |
| `services/contract/user-openapi.yaml` | ✅ |
| `services/backend/` — all ledger `.kt` files | ✅ |
| `services/front-end/src/domains/ledger/` — all files | ✅ |
| `services/front-end/src/domains/user/` — all files | ✅ |
| `services/front-end/src/shared/` — all files | ✅ |
| `decisions/` — all entries | ✅ |
| `tasks/` — existing task files | ✅ |

---

## Ambiguities resolved

| Item | Resolution |
|---|---|
| `LedgerEntry.kt` uses `@GeneratedValue` | Pre-existing deviation from `standards/backend.md`. DB-1 fixes this forward by switching to `Persistable<UUID>` pattern with a pre-assigned UUID. |
| `LedgerEntry.kt` missing `currency` and `description` fields | Domain model doc specifies both. DB-1 adds them. |
| "Always-fresh fetch on navigation to /accounts" | Implemented via `staleTime: 0` on the `useAccounts` TanStack Query hook in STATE-1. |
| Top-up response shape | New `TopUpAccountResponse` schema introduced — does not reuse `AccountResponse`. |

---

## DB Layer

### DB-1 — Fix `LedgerEntry` entity: add `currency`, `description`, switch to `Persistable` pattern

**Layer:** Database
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — step 9 (LedgerEntry created with currency and description)
**Inputs:**
- Existing `org.dpp.tradelab.ledger.model.LedgerEntry`
**Outputs:**
- Updated `LedgerEntry.kt` with:
  - `currency: String` — `@Column(nullable = false, updatable = false)`
  - `description: String?` — `@Column(nullable = true, updatable = false)`
  - UUID primary key pre-assigned by service layer (no `@GeneratedValue`)
  - Implements `Persistable<UUID>` with `@Transient _isNew: Boolean = true`
  - Plain `class`, not `data class`
  - Manual `equals`, `hashCode` (on `id`), and `toString`

**Acceptance criteria:**
- [ ] `currency: String` field present with `@Column(nullable = false, updatable = false)`
- [ ] `description: String?` field present with `@Column(nullable = true, updatable = false)`
- [ ] `@GeneratedValue` removed; UUID is a constructor parameter pre-assigned by the caller
- [ ] Implements `Persistable<UUID>` with `@Transient _isNew: Boolean = true`
- [ ] `getId()` returns the UUID; `isNew()` returns `_isNew`
- [ ] Entity is plain `class`, not `data class`
- [ ] `equals` and `hashCode` implemented based on `id` only
- [ ] `toString` implemented manually
- [ ] All existing fields (`id`, `accountId`, `type`, `assetType`, `amount`, `createdAt`) retained with correct annotations
- [ ] Unit test: construct a `LedgerEntry` with all fields including `currency` and `description`; assert all properties return the expected values

**Depends on:** none

---

### DB-2 — Make `Account.balance` mutable

**Layer:** Database
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — step 8 (Account.balance updated by top-up amount)
**Inputs:**
- Existing `org.dpp.tradelab.ledger.model.Account`
**Outputs:**
- Updated `Account.kt` where `balance` is declared `var` instead of `val`
- `balance` `@Column` annotation does not have `updatable = false`

**Acceptance criteria:**
- [ ] `balance` declared as `var`
- [ ] `balance` `@Column` does not carry `updatable = false`
- [ ] All other fields, annotations, `Persistable` implementation, `equals`, `hashCode`, and `toString` are unchanged
- [ ] Unit test: construct an `Account`, mutate `balance`, assert the new value is set

**Depends on:** none

---

## REPO Layer

### REPO-1 — Create `LedgerEntryRepository`

**Layer:** Repository
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — step 9 (persisting the new LedgerEntry)
**Inputs:**
- `LedgerEntry` entity (from DB-1)
**Outputs:**
- New file `org.dpp.tradelab.ledger.repository.LedgerEntryRepository`
- Extends `JpaRepository<LedgerEntry, UUID>`
- Annotated `@Repository`

**Acceptance criteria:**
- [ ] `LedgerEntryRepository` interface exists in `ledger.repository`
- [ ] Extends `JpaRepository<LedgerEntry, UUID>`
- [ ] Annotated `@Repository`
- [ ] No custom query methods required (Spring Data `save` is sufficient for this use case)
- [ ] Repository slice test (`@DataJpaTest`): save a `LedgerEntry` with `currency` and `description`; find it by id; assert all fields match

**Depends on:** DB-1

---

## EXCEPTION Layer

### EXCEPTION-1 — Create `AccountNotFoundException`

**Layer:** Exception
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — error case "Account not found"
**Inputs:** `accountId: UUID`
**Outputs:**
- New file `org.dpp.tradelab.ledger.exception.AccountNotFoundException`
- Extends `RuntimeException`
- `GlobalExceptionHandler` maps it to HTTP 404

**Acceptance criteria:**
- [ ] Class exists in `ledger.exception`
- [ ] Constructor takes `accountId: UUID`; message is `"No account found with id: $accountId"`
- [ ] `GlobalExceptionHandler` has a handler for `AccountNotFoundException` that returns HTTP 404 with the standard `ErrorResponse` shape
- [ ] Unit test on `GlobalExceptionHandler`: throwing `AccountNotFoundException` produces an HTTP 404 response with the correct error body

**Depends on:** none

---

### EXCEPTION-2 — Create `AccountNotActiveException`

**Layer:** Exception
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — error cases "Account not owned by user" and "Account not active"
**Inputs:** `accountId: UUID`
**Outputs:**
- New file `org.dpp.tradelab.ledger.exception.AccountNotActiveException`
- Extends `RuntimeException`
- `GlobalExceptionHandler` maps it to HTTP 403

**Acceptance criteria:**
- [ ] Class exists in `ledger.exception`
- [ ] Constructor takes `accountId: UUID`; message is `"Account $accountId is not available for this operation"`
- [ ] `GlobalExceptionHandler` has a handler for `AccountNotActiveException` that returns HTTP 403 with the standard `ErrorResponse` shape
- [ ] Unit test on `GlobalExceptionHandler`: throwing `AccountNotActiveException` produces an HTTP 403 response with the correct error body

**Depends on:** none

---

## EVT Layer

### EVT-1 — Create `AccountToppedUpEvent` data class

**Layer:** Event
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — step 10 (emit `AccountToppedUp`)
**Inputs:** none
**Outputs:**
- New file `org.dpp.tradelab.ledger.messaging.AccountToppedUpEvent`
- `data class` with fields: `accountId: UUID`, `userId: UUID`, `amount: BigDecimal`, `currency: String`, `newBalance: BigDecimal`, `ledgerEntryId: UUID`, `timestamp: Instant`

**Acceptance criteria:**
- [ ] `data class AccountToppedUpEvent` in `ledger.messaging`
- [ ] All seven payload fields present with correct Kotlin types
- [ ] Name follows `standards/backend.md` convention: `{Entity}{Action}Event`
- [ ] No JPA entity references in payload — primitive types and UUIDs only
- [ ] No `@EventListener` required in this task — publishing only (listener is out of scope for this use case)

**Depends on:** none

---

## SVC Layer

### SVC-1 — Add `topUpAccount` method to `AccountService`

**Layer:** Service
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — steps 7, 8, 9, 10
**Inputs:**
- `accountId: UUID` — target account
- `userId: UUID` — authenticated user (passed in by the controller; taken from the request body)
- `amount: BigDecimal` — top-up amount
**Outputs:**
- New `topUpAccount(accountId: UUID, userId: UUID, amount: BigDecimal): Account` method on `AccountService`
- Persisted updated `Account` (new balance)
- Persisted new `LedgerEntry` (CREDIT, CASH, pre-assigned UUID)
- `AccountToppedUpEvent` published

**Method logic (in order):**
1. Validate `amount > 0` — throw `IllegalArgumentException("amount must be greater than zero")` if not
2. Validate `amount` is a whole number (i.e. `amount.stripTrailingZeros().scale() <= 0`) — throw `IllegalArgumentException("amount must be a whole number")` if not
3. Validate `amount <= BigDecimal(10_000_000)` — throw `IllegalArgumentException("amount must not exceed 10,000,000")` if not
4. Load `Account` by `accountId` via `accountRepository.findById(accountId)` — throw `AccountNotFoundException(accountId)` if absent
5. Validate `account.userId == userId` — throw `AccountNotActiveException(accountId)` if not
6. Validate `account.status == AccountStatus.ACTIVE` — throw `AccountNotActiveException(accountId)` if not
7. Update `account.balance += amount`; save account via `accountRepository.save(account)`
8. Pre-assign a UUID for the ledger entry; create and save a `LedgerEntry` via `ledgerEntryRepository.save(...)` with `type = CREDIT`, `assetType = CASH`, `amount = amount`, `currency = account.currency.name`, `description = "Top-up"`
9. Publish `AccountToppedUpEvent` via `eventPublisher.publishEvent(...)`
10. Return the saved account

**Acceptance criteria:**
- [ ] Method annotated `@Transactional`
- [ ] All validation steps execute in the order above
- [ ] `IllegalArgumentException` thrown for invalid amount (GlobalExceptionHandler maps this to HTTP 400)
- [ ] `AccountNotFoundException` thrown when account absent
- [ ] `AccountNotActiveException` thrown when userId mismatch or status not ACTIVE
- [ ] Account balance is correctly incremented and saved
- [ ] `LedgerEntry` saved with correct field values; UUID pre-assigned via `UUID.randomUUID()`
- [ ] `AccountToppedUpEvent` published with all seven payload fields
- [ ] Method returns the saved (updated) `Account`
- [ ] KoTest unit tests covering:
  - Happy path: balance increases by amount, entry saved, event published, updated account returned
  - `amount = 0` → `IllegalArgumentException`
  - `amount = -1` → `IllegalArgumentException`
  - `amount = 1.5` (decimal) → `IllegalArgumentException`
  - `amount = 10_000_001` → `IllegalArgumentException`
  - Account not found → `AccountNotFoundException`
  - `userId` mismatch → `AccountNotActiveException`
  - Account `status = SUSPENDED` → `AccountNotActiveException`
  - Account `status = CLOSED` → `AccountNotActiveException`

**Depends on:** DB-1, DB-2, REPO-1, EXCEPTION-1, EXCEPTION-2, EVT-1

---

## API-CONTRACT Layer

### API-CONTRACT-1 — Add `POST /accounts/{accountId}/top-up` to `ledger-openapi.yaml`

**Layer:** OpenAPI Contract
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — step 11 (HTTP 200 response contract); all error cases (400, 401, 403, 404)
**Inputs:**
- Existing `services/contract/ledger-openapi.yaml`
**Outputs:**
- Updated `services/contract/ledger-openapi.yaml` with new path `/accounts/{accountId}/top-up` and new schemas `TopUpAccountRequest` and `TopUpAccountResponse` appended
- All existing paths and schemas unchanged

**New path:**

```yaml
/accounts/{accountId}/top-up:
  post:
    tags:
      - Ledger
    summary: Top up an account balance
    operationId: topUpAccount
    parameters:
      - name: accountId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    requestBody:
      required: true
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/TopUpAccountRequest'
    responses:
      '200': TopUpAccountResponse
      '400': ErrorResponse
      '401': ErrorResponse
      '403': ErrorResponse
      '404': ErrorResponse
```

**New schemas:**

`TopUpAccountRequest`:
- `userId` (string, format: uuid, required)
- `amount` (integer, minimum: 1, maximum: 10000000, required)

`TopUpAccountResponse`:
- `accountId` (string, format: uuid, required)
- `newBalance` (number, required)
- `currency` (string, enum: [USD, GBP, EUR], required)
- `ledgerEntryId` (string, format: uuid, required)
- `timestamp` (string, format: date-time, required)

**Acceptance criteria:**
- [ ] `POST /accounts/{accountId}/top-up` path defined with `operationId: topUpAccount`
- [ ] `accountId` path parameter: `string`, `format: uuid`, `required: true`
- [ ] `TopUpAccountRequest` schema: `userId` (uuid, required), `amount` (integer, minimum 1, maximum 10000000, required)
- [ ] `TopUpAccountResponse` schema: all five fields required with correct types
- [ ] HTTP 200 response references `TopUpAccountResponse`
- [ ] HTTP 400, 401, 403, 404 responses each reference `ErrorResponse`
- [ ] Existing `ErrorResponse` schema reused — not duplicated
- [ ] Existing paths (`POST /accounts`, `GET /accounts`) and schemas (`OpenAccountRequest`, `AccountResponse`, `AccountListResponse`) unchanged
- [ ] YAML is valid OpenAPI 3.0.3

**Depends on:** none (written before backend code generation runs)

---

## CONTROLLER Layer

### CONTROLLER-1 — Implement `topUpAccount` in `LedgerApiDelegateImpl`

**Layer:** Controller
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — steps 7–11 (receives HTTP request, delegates to service, maps response)
**Inputs:**
- Generated `AccountsApiDelegate.topUpAccount(accountId: UUID, topUpAccountRequest: TopUpAccountRequest)` from API-CONTRACT-1
- `AccountService.topUpAccount(...)` from SVC-1
**Outputs:**
- Override of `topUpAccount` in `LedgerApiDelegateImpl`
- Returns `ResponseEntity.ok(TopUpAccountResponse(...))`

**Acceptance criteria:**
- [ ] `topUpAccount` override present in `LedgerApiDelegateImpl`
- [ ] Extracts `userId` and `amount` from the generated `TopUpAccountRequest` DTO
- [ ] Calls `accountService.topUpAccount(accountId, userId, amount.toBigDecimal())` — no business logic in the controller
- [ ] Maps the returned `Account` and the persisted `LedgerEntry` data to the generated `TopUpAccountResponse` DTO
- [ ] Returns `ResponseEntity.ok(topUpAccountResponse)`
- [ ] No entity objects serialised directly — DTOs only
- [ ] MockMvc (`@SpringBootTest` + `MockMvc`) tests:
  - Valid request → HTTP 200 with correct `TopUpAccountResponse` body
  - Service throws `AccountNotFoundException` → HTTP 404
  - Service throws `AccountNotActiveException` → HTTP 403
  - Service throws `IllegalArgumentException` → HTTP 400

**Depends on:** SVC-1, API-CONTRACT-1

---

## CLI Layer

### CLI-1 — Add `topUpAccount` function and types to the ledger API client

**Layer:** API Client
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — step 5/6 (frontend submits top-up to backend)
**Inputs:**
- `services/contract/ledger-openapi.yaml` — `POST /accounts/{accountId}/top-up` (from API-CONTRACT-1)
- Existing `src/domains/ledger/api/accountApi.ts`
- Existing `src/domains/ledger/types/account.ts`
**Outputs:**
- Two new TypeScript interfaces added to `src/domains/ledger/types/account.ts`:
  - `TopUpAccountRequest`: `{ userId: string; amount: number }`
  - `TopUpAccountResponse`: `{ accountId: string; newBalance: number; currency: string; ledgerEntryId: string; timestamp: string }`
- New exported constant `TOP_UP_ACCOUNT_KEY = 'topUpAccount'` in `accountApi.ts`
- New exported async function in `accountApi.ts`:
  ```ts
  topUpAccount(accountId: string, request: TopUpAccountRequest): Promise<TopUpAccountResponse>
  ```
  — calls `POST /v1/accounts/${accountId}/top-up` via the shared `axiosInstance`

**Acceptance criteria:**
- [ ] `TopUpAccountRequest` and `TopUpAccountResponse` interfaces present in `account.ts` with correct field names and types
- [ ] `topUpAccount` calls `axiosInstance.post<TopUpAccountResponse>(\`/v1/accounts/${accountId}/top-up\`, request)`
- [ ] `TOP_UP_ACCOUNT_KEY` exported as a string constant
- [ ] Existing exports (`createAccount`, `fetchAccounts`, `ACCOUNTS_QUERY_KEY`, `OpenAccountRequest`, `AccountResponse`, `AccountListResponse`) unchanged
- [ ] Unit tests in `accountApi.test.ts`:
  - Success → resolves with `TopUpAccountResponse`
  - HTTP 400 → throws `AxiosError` with `response.status === 400`
  - HTTP 403 → throws `AxiosError` with `response.status === 403`
  - HTTP 404 → throws `AxiosError` with `response.status === 404`

**Depends on:** API-CONTRACT-1

---

## STATE Layer

### STATE-1 — Add `useTopUpAccount` hook and apply `staleTime: 0` to `useAccounts`

**Layer:** State
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — steps 6, 13, 14; use case happy path step 1 (always-fresh accounts on navigation)
**Inputs:**
- `topUpAccount` function and `TOP_UP_ACCOUNT_KEY` (from CLI-1)
- `ACCOUNTS_QUERY_KEY` (existing in `accountApi.ts`)
- Existing `src/domains/ledger/hooks/useLedger.ts`
**Outputs:**
- New exported hook `useTopUpAccount` added to `useLedger.ts`
- `useAccounts` updated with `staleTime: 0`

**`useTopUpAccount` signature:**
```ts
useTopUpAccount(): UseMutationResult
```
- `mutationFn`: calls `topUpAccount(accountId, { userId, amount })`
- `onSuccess`: calls `queryClient.invalidateQueries({ queryKey: [ACCOUNTS_QUERY_KEY, userId] })`
- `userId` read from `useSessionStore` (same pattern as `useOpenAccount`)

**`useAccounts` change:**
- Add `staleTime: 0` to the `useQuery` config so data is always re-fetched on component mount (i.e. every time the user navigates to `/accounts`)

**Acceptance criteria:**
- [ ] `useTopUpAccount` exported from `useLedger.ts`
- [ ] `mutationFn` calls `topUpAccount` with correct `accountId` and request body
- [ ] `onSuccess` invalidates `[ACCOUNTS_QUERY_KEY, userId]`
- [ ] `useAccounts` has `staleTime: 0` — verified by test that shows query re-fires on remount even when cached data exists
- [ ] Existing `useOpenAccount` hook is unchanged
- [ ] Unit tests in `useLedger.test.ts`:
  - `useTopUpAccount` success → `invalidateQueries` called with `[ACCOUNTS_QUERY_KEY, userId]`
  - `useTopUpAccount` error → `isError` is true; `invalidateQueries` not called
  - `useAccounts` with `staleTime: 0` → query fires on every mount

**Depends on:** CLI-1

---

## COMP Layer

### COMP-1 — Create `TopUpModal` component

**Layer:** Component
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — steps 2, 3, 4, 5, 6, 12 (modal UI, amount input, client-side validation, loading, confirmation)
**Inputs:**
- `account: AccountResponse` — account being topped up (name and currency displayed)
- `onConfirm: (amount: number) => void` — called on valid submission
- `onClose: () => void` — called to dismiss
- `isLoading: boolean`
- `isSuccess: boolean`
- `error?: string`
**Outputs:**
- New file `src/domains/ledger/components/TopUpModal.tsx`
- New file `src/domains/ledger/components/TopUpModal.test.tsx`

**Component states:**
1. **Default (form visible):** account name + currency displayed; single "Amount" numeric input; "Confirm" and "Cancel" buttons
2. **Loading:** Confirm button disabled and shows loading text; input disabled
3. **Success (`isSuccess = true`):** Form hidden; green tick icon + text "Top up successful"
4. **Error:** `error` string shown above the form

**Client-side validation (inline, on change and on submit):**
- Not a whole number → "Amount must be a whole number."
- `< 1` → "Amount must be at least 1."
- `> 10,000,000` → "Amount must not exceed 10,000,000."
- Confirm button disabled while any validation error is present or field is empty

**Acceptance criteria:**
- [ ] `TopUpModalProps` interface explicitly typed
- [ ] Account name and currency rendered
- [ ] Amount input validates inline on change: whole number, min 1, max 10,000,000
- [ ] Correct inline error messages per rule above
- [ ] Confirm button disabled when input invalid, empty, or `isLoading` is true
- [ ] `isLoading` state: Confirm shows loading text and is disabled; input disabled
- [ ] `isSuccess` state: form hidden; green tick and "Top up successful" shown
- [ ] `error` prop: error message rendered above form
- [ ] Cancel button calls `onClose`
- [ ] `onConfirm` called with the parsed integer amount on valid submit
- [ ] No API calls or store access inside the component
- [ ] Unit tests in `TopUpModal.test.tsx`:
  - Renders account name and currency
  - Decimal input (`1.5`) → inline error "Amount must be a whole number."
  - Amount `0` → inline error "Amount must be at least 1."
  - Negative amount → inline error "Amount must be at least 1."
  - Amount `10000001` → inline error "Amount must not exceed 10,000,000."
  - Valid amount (`500`) → `onConfirm` called with `500`; no error shown
  - `isLoading = true` → Confirm button disabled
  - `isSuccess = true` → success message shown; form not visible
  - `error` prop set → error message rendered
  - Cancel clicked → `onClose` called

**Depends on:** none

---

### COMP-2 — Add "Top Up" button to `AccountList`

**Layer:** Component
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — step 1 (user clicks "Top Up" on an account card)
**Inputs:**
- Existing `src/domains/ledger/components/AccountList.tsx`
- New prop: `onTopUp: (account: AccountResponse) => void`
**Outputs:**
- Updated `AccountList.tsx` with "Top Up" button on each account card
- Updated `AccountListProps` to include `onTopUp: (account: AccountResponse) => void`
- Updated `AccountList.test.tsx`

**Acceptance criteria:**
- [ ] Each account card renders a "Top Up" button
- [ ] Clicking "Top Up" on a card calls `onTopUp(account)` with that exact account object
- [ ] `onTopUp` is a required prop in `AccountListProps`
- [ ] All existing account card fields (name, currency, balance, status, createdAt) still render
- [ ] All existing tests pass without modification
- [ ] New unit tests:
  - "Top Up" button is present for each rendered account
  - Clicking "Top Up" on a specific account calls `onTopUp` with that account (not another)

**Depends on:** none

---

## SCREEN Layer

### SCREEN-1 — Wire top-up flow in `AccountsPage`

**Layer:** Screen
**Domain:** ledger
**Use case:** account-top-up
**Implements:** account-top-up flow — steps 1–14 (end-to-end page orchestration)
**Inputs:**
- Existing `src/domains/ledger/pages/AccountsPage.tsx`
- `useTopUpAccount` hook (from STATE-1)
- `TopUpModal` component (from COMP-1)
- Updated `AccountList` with `onTopUp` prop (from COMP-2)
**Outputs:**
- Updated `AccountsPage.tsx`
- Updated `AccountsPage.test.tsx`

**New state and behaviour:**
- Add `selectedAccount: AccountResponse | null` (initially `null`)
- Pass `onTopUp={(account) => setSelectedAccount(account)}` to `AccountList`
- When `selectedAccount !== null`: render `<TopUpModal>` for that account
- `TopUpModal.onConfirm(amount)`: call `useTopUpAccount.mutate({ accountId: selectedAccount.accountId, userId: user.userId, amount })`
  - On 401: `navigate('/login', { replace: true })`
  - On other errors: derive a user-facing error string and pass to `TopUpModal` via local state
- `TopUpModal.onClose`: clear `selectedAccount` and reset any top-up error state; also reset mutation state
- Pass to `TopUpModal`:
  - `isLoading={useTopUpAccount.isPending}`
  - `isSuccess={useTopUpAccount.isSuccess}`
  - `error={topUpError}` (local string state, set on mutation error)
- Only one modal open at a time (selecting a new account while one is open replaces it)

**Error string mapping:**
- HTTP 400 → `"Invalid amount. Please check your input."`
- HTTP 403 → `"This account is not available for top-up."`
- HTTP 404 → `"Account not found."`
- Other → `"Something went wrong. Please try again."`

**Acceptance criteria:**
- [ ] Clicking "Top Up" on an account card opens `TopUpModal` for that account
- [ ] Only one `TopUpModal` rendered at a time
- [ ] `TopUpModal` receives correct `account`, `isLoading`, `isSuccess`, `error` props
- [ ] Successful mutation: `isSuccess` passed as `true` → user sees "Top up successful" confirmation in modal
- [ ] After modal is closed (`onClose`): `selectedAccount` cleared, mutation state reset
- [ ] HTTP 401 from mutation: `navigate('/login', { replace: true })` called
- [ ] HTTP 403 from mutation: correct error string passed to `TopUpModal`
- [ ] HTTP 404 from mutation: correct error string passed to `TopUpModal`
- [ ] HTTP 400 from mutation: correct error string passed to `TopUpModal`
- [ ] `AccountList` receives `onTopUp` handler
- [ ] Accounts list re-fetches automatically after top-up (via query invalidation in STATE-1 — no extra wiring needed)
- [ ] Existing open-account form behaviour unchanged
- [ ] No business logic in the page — mutation logic delegated to `useTopUpAccount`
- [ ] Unit tests in `AccountsPage.test.tsx`:
  - Clicking "Top Up" on an account opens `TopUpModal` with that account
  - Successful top-up: `isSuccess=true` passed to modal
  - Closing modal clears `selectedAccount`
  - HTTP 401 error: `navigate('/login')` called
  - HTTP 403 error: correct error string passed to modal
  - HTTP 404 error: correct error string passed to modal

**Depends on:** STATE-1, COMP-1, COMP-2

---

## Dependency summary

| Task ID | Title | Depends on |
|---|---|---|
| DB-1 | Fix `LedgerEntry`: add `currency`, `description`, switch to `Persistable` | none |
| DB-2 | Make `Account.balance` mutable | none |
| REPO-1 | Create `LedgerEntryRepository` | DB-1 |
| EXCEPTION-1 | Create `AccountNotFoundException` | none |
| EXCEPTION-2 | Create `AccountNotActiveException` | none |
| EVT-1 | Create `AccountToppedUpEvent` | none |
| SVC-1 | Add `topUpAccount` to `AccountService` | DB-1, DB-2, REPO-1, EXCEPTION-1, EXCEPTION-2, EVT-1 |
| API-CONTRACT-1 | Add `POST /accounts/{accountId}/top-up` to `ledger-openapi.yaml` | none |
| CONTROLLER-1 | Implement `topUpAccount` in `LedgerApiDelegateImpl` | SVC-1, API-CONTRACT-1 |
| CLI-1 | Add `topUpAccount` to `accountApi.ts` | API-CONTRACT-1 |
| STATE-1 | Add `useTopUpAccount`, apply `staleTime: 0` to `useAccounts` | CLI-1 |
| COMP-1 | Create `TopUpModal` component | none |
| COMP-2 | Add "Top Up" button to `AccountList` | none |
| SCREEN-1 | Wire top-up flow in `AccountsPage` | STATE-1, COMP-1, COMP-2 |
