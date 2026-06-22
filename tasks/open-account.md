# Tasks: Open an Account

**Use case:** `domain/usecases/open-account.md`  
**Flow:** `domain/flows/open-account.md`  
**Generated:** 2026-06-22

---

## Files read during decomposition

| File | Status |
|---|---|
| `AGENTS.md` | ✅ read |
| `domain/model/account.md` | ✅ read |
| `domain/model/ledger-entry.md` | ✅ read |
| `domain/model/user.md` | ✅ read |
| `domain/model/session.md` | ✅ read |
| `domain/flows/open-account.md` | ✅ read |
| `domain/flows/user-session.md` | ✅ read |
| `domain/usecases/open-account.md` | ✅ read |
| `standards/architecture.md` | ✅ read — fully populated |
| `standards/backend.md` | ✅ read — fully populated |
| `standards/frontend.md` | ✅ read — fully populated |
| `services/contract/user-openapi.yaml` | ✅ read — used as structural pattern reference |
| `decisions/2026-06-22-account-ledger-model.md` | ✅ read |

---

## Use case summary

**Actor:** Authenticated User (logged-in, `active` status).  
**Goal:** Open a new paper trading account by selecting a base currency (`USD` / `GBP` / `EUR`) and an optional name, then see it appear in the accounts list on `/accounts`.  
**Flows involved:** `open-account` (primary — 14 steps) and `user-session` Flow A (sidebar Accounts link).  
**Models involved:** `Account` (created), `User` (read for status validation), `LedgerEntry` (not touched — history starts empty).  
**Events emitted:** `AccountOpened` — payload `accountId`, `userId`, `currency`, `name`, `timestamp`.

---

## Ambiguities and gaps

**Resolved — `GET /api/v1/accounts` query parameter:** The flow doc requires the Accounts page to display the user's existing accounts. No server-side session exists in this iteration (confirmed by `decisions/2026-06-20-login-response-and-session.md`). The `userId` is available in the frontend Zustand session store. `GET /api/v1/accounts?userId={userId}` is used, consistent with the `GET /api/v1/users/{userId}` pattern already established.

**No blocking ambiguities.**

---

## Dependency summary

| Task ID | Title | Depends on |
|---------|-------|------------|
| DB-1 | Account JPA entity | none |
| DB-2 | LedgerEntry JPA entity | none |
| REPO-1 | AccountRepository | DB-1 |
| EXCEPTION-1 | UserNotFoundException (ledger) | none |
| EXCEPTION-2 | InvalidCurrencyException | none |
| EVT-1 | AccountOpenedEvent data class | DB-1 |
| SVC-1 | AccountService: openAccount | REPO-1, EVT-1, EXCEPTION-2 |
| SVC-2 | AccountService: listAccountsByUser | REPO-1 |
| API-CONTRACT-1 | Create ledger-openapi.yaml | none |
| CONTROLLER-1 | LedgerApiDelegateImpl | SVC-1, SVC-2, API-CONTRACT-1 |
| CLI-1 | Ledger API client | API-CONTRACT-1 |
| STATE-1 | useAccounts + useOpenAccount hooks | CLI-1 |
| COMP-1 | AccountList component | CLI-1 (type only) |
| COMP-2 | OpenAccountForm component | none |
| SCREEN-1 | AccountsPage screen | STATE-1, COMP-1, COMP-2 |

---

## DB Layer

### DB-1 — Account JPA entity

**Layer:** Database  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — step 10 (create account record)  
**Inputs:**
- `domain/model/account.md` — field definitions, types, and business rules

**Outputs:**
- `org.dpp.tradelab.ledger.model.Account` — JPA `@Entity` class
- `org.dpp.tradelab.ledger.model.Currency` — Kotlin `enum class` with values `USD`, `GBP`, `EUR`
- `org.dpp.tradelab.ledger.model.AccountStatus` — Kotlin `enum class` with values `ACTIVE`, `SUSPENDED`, `CLOSED`

**Acceptance criteria:**
- [ ] `Account` is annotated `@Entity` and maps to table `account`
- [ ] Primary key `id` is `UUID` with `@GeneratedValue(strategy = GenerationType.UUID)`
- [ ] `userId` is `UUID`, mapped `@Column(nullable = false)` — no JPA relationship to `User`; stored as raw UUID per architecture cross-domain rules
- [ ] `name` is `String`, mapped `@Column(nullable = false)`
- [ ] `currency` is `Currency` enum, mapped `@Enumerated(EnumType.STRING)`, `@Column(nullable = false)`
- [ ] `balance` is `BigDecimal`, mapped `@Column(precision = 19, scale = 4, nullable = false)`
- [ ] `status` is `AccountStatus` enum, mapped `@Enumerated(EnumType.STRING)`, `@Column(nullable = false)`
- [ ] `createdAt` uses `@CreationTimestamp`, stored UTC
- [ ] `updatedAt` uses `@UpdateTimestamp`, stored UTC
- [ ] `Currency` enum contains exactly `USD`, `GBP`, `EUR`
- [ ] `AccountStatus` enum contains exactly `ACTIVE`, `SUSPENDED`, `CLOSED`
- [ ] No business logic in the entity class

**Depends on:** none

---

### DB-2 — LedgerEntry JPA entity

**Layer:** Database  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** `domain/model/ledger-entry.md` — entity definition (no entries are created during account opening, but the schema must exist for future flows)  
**Inputs:**
- `domain/model/ledger-entry.md` — field definitions, types, and business rules

**Outputs:**
- `org.dpp.tradelab.ledger.model.LedgerEntry` — JPA `@Entity` class
- `org.dpp.tradelab.ledger.model.EntryType` — Kotlin `enum class` with values `CREDIT`, `DEBIT`
- `org.dpp.tradelab.ledger.model.AssetType` — Kotlin `enum class` with values `CASH`, `STOCK`

**Acceptance criteria:**
- [ ] `LedgerEntry` is annotated `@Entity` and maps to table `ledger_entry`
- [ ] Primary key `id` is `UUID` with `@GeneratedValue(strategy = GenerationType.UUID)`
- [ ] `accountId` is `UUID`, mapped `@Column(nullable = false)` — no JPA relationship to `Account`; stored as raw UUID
- [ ] `type` is `EntryType` enum, mapped `@Enumerated(EnumType.STRING)`, `@Column(nullable = false)`
- [ ] `assetType` is `AssetType` enum, mapped `@Enumerated(EnumType.STRING)`, `@Column(nullable = false)`
- [ ] `amount` is `BigDecimal`, mapped `@Column(precision = 19, scale = 4, nullable = false)`
- [ ] `currency` is `String`, mapped `@Column(nullable = false)`
- [ ] `description` is `String?`, mapped `@Column(nullable = true)`
- [ ] `createdAt` uses `@CreationTimestamp`, stored UTC — no `updatedAt` field (entity is immutable after creation)
- [ ] `EntryType` enum contains exactly `CREDIT`, `DEBIT`
- [ ] `AssetType` enum contains exactly `CASH`, `STOCK`
- [ ] No business logic in the entity class

**Depends on:** none

---

## REPO Layer

### REPO-1 — AccountRepository

**Layer:** Repository  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — step 10 (persist account), step 2 (list accounts for user)  
**Inputs:**
- `Account` entity (`org.dpp.tradelab.ledger.model.Account`)

**Outputs:**
- `org.dpp.tradelab.ledger.repository.AccountRepository` — Spring Data JPA interface

**Acceptance criteria:**
- [ ] Extends `JpaRepository<Account, UUID>`
- [ ] Declares custom method `findAllByUserId(userId: UUID): List<Account>` — used to retrieve all accounts belonging to a user
- [ ] No other custom methods required for this use case
- [ ] No business logic in the interface

**Depends on:** DB-1

---

## EXCEPTION Layer

### EXCEPTION-1 — UserNotFoundException (ledger domain)

**Layer:** Exception  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — error case "User not active" (step 9)  
**Inputs:** `userId: UUID`  
**Outputs:**
- `org.dpp.tradelab.ledger.exception.UserNotFoundException`

**Acceptance criteria:**
- [ ] Extends `RuntimeException`
- [ ] Constructor accepts `userId: UUID` and produces a message of the form `"No active user found with id: {userId}"`
- [ ] No other fields or methods required

**Depends on:** none

---

### EXCEPTION-2 — InvalidCurrencyException

**Layer:** Exception  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — error case "Unsupported currency" (step 9)  
**Inputs:** `currency: String`  
**Outputs:**
- `org.dpp.tradelab.ledger.exception.InvalidCurrencyException`

**Acceptance criteria:**
- [ ] Extends `RuntimeException`
- [ ] Constructor accepts `currency: String` and produces a message of the form `"Unsupported currency: {currency}"`
- [ ] No other fields or methods required

**Depends on:** none

---

## EVT Layer

### EVT-1 — AccountOpenedEvent data class

**Layer:** Event  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — step 11 (emit `AccountOpened`)  
**Inputs:**
- `domain/model/account.md` events section — `AccountOpened` payload: `accountId`, `userId`, `currency`, `name`, `timestamp`

**Outputs:**
- `org.dpp.tradelab.ledger.messaging.AccountOpenedEvent` — Kotlin `data class`

**Acceptance criteria:**
- [ ] Is a Kotlin `data class`
- [ ] Fields: `accountId: UUID`, `userId: UUID`, `currency: Currency`, `name: String`, `timestamp: Instant`
- [ ] No Spring annotations on the class itself
- [ ] Class name is exactly `AccountOpenedEvent` — matches the event name in the flow doc

**Depends on:** DB-1 (references `Currency` enum)

---

## SVC Layer

### SVC-1 — AccountService: openAccount

**Layer:** Service  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — steps 9, 10, 11, 12 (validate, create, emit, return)  
**Inputs:**
- `userId: UUID` — from the controller
- `currency: Currency` — selected base currency enum value
- `name: String?` — optional label; `null` or blank means default to `"account-{id}"`

**Outputs:**
- `Account` — the persisted account entity (returned to the controller)
- `AccountOpenedEvent` published via `ApplicationEventPublisher`

**Acceptance criteria:**
- [ ] Method signature: `fun openAccount(userId: UUID, currency: Currency, name: String?): Account`
- [ ] Annotated `@Transactional`
- [ ] Sets `balance` to `BigDecimal.ZERO` and `status` to `AccountStatus.ACTIVE`
- [ ] Resolves `name`: if the supplied value is non-null and non-blank, uses it as-is; if null or blank, sets `name` to `"account-${account.id}"` using the actual persisted UUID (requires saving once to obtain the ID, then updating `name` and saving again if the default was needed)
- [ ] Persists the account via `AccountRepository.save()`
- [ ] After the record is persisted with its final `name`, publishes `AccountOpenedEvent(accountId = account.id, userId = userId, currency = currency, name = account.name, timestamp = Instant.now())` via `ApplicationEventPublisher`
- [ ] Returns the persisted `Account`
- [ ] Unit tests cover:
  - [ ] Happy path — explicit name: account saved with provided name, event published with correct payload
  - [ ] Happy path — default name: account saved, `name` equals `"account-${account.id}"`
  - [ ] `InvalidCurrencyException` is thrown when an unsupported currency string is passed (tests the guard path — in practice the enum type enforces this at compile time, but the service must handle conversion errors from the controller layer gracefully)

**Depends on:** REPO-1, EVT-1, EXCEPTION-2

---

### SVC-2 — AccountService: listAccountsByUser

**Layer:** Service  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — step 2 (render list of existing accounts)  
**Inputs:**
- `userId: UUID`

**Outputs:**
- `List<Account>` — all accounts owned by the user (may be empty)

**Acceptance criteria:**
- [ ] Method signature: `fun listAccountsByUser(userId: UUID): List<Account>`
- [ ] Annotated `@Transactional(readOnly = true)`
- [ ] Delegates to `AccountRepository.findAllByUserId(userId)`
- [ ] Returns an empty list (not an error) when the user has no accounts
- [ ] Unit tests cover:
  - [ ] User with multiple accounts: all returned
  - [ ] User with no accounts: empty list returned

**Depends on:** REPO-1

---

## API-CONTRACT Layer

### API-CONTRACT-1 — Create services/contract/ledger-openapi.yaml

**Layer:** OpenAPI Contract  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — steps 9–12 (`POST /api/v1/accounts`); step 2 (`GET /api/v1/accounts`)  
**Inputs:**
- `POST /api/v1/accounts` — request body: `userId` (UUID, required), `currency` (string enum `USD|GBP|EUR`, required), `name` (string, optional); responses: 201 `AccountResponse`, 400 `ErrorResponse`, 401 `ErrorResponse`, 403 `ErrorResponse`
- `GET /api/v1/accounts` — query param: `userId` (string/uuid, required); responses: 200 `AccountListResponse`, 401 `ErrorResponse`
- `services/contract/user-openapi.yaml` — structural pattern reference

**Outputs:**
- `services/contract/ledger-openapi.yaml` — new file, complete OpenAPI 3.0.3 contract for the ledger domain

**Acceptance criteria:**
- [ ] `info.title` is `"Trade Lab API — Ledger"`
- [ ] `info.version` is `"1.0.0"`
- [ ] `servers[0].url` is `http://localhost:8080/api/v1`
- [ ] `POST /api/v1/accounts` defined with `operationId: openAccount`
  - [ ] Request body schema `OpenAccountRequest`: `userId` (string/uuid, required), `currency` (string enum `[USD, GBP, EUR]`, required), `name` (string, minLength 1, optional)
  - [ ] 201 response schema `AccountResponse`: `accountId` (string/uuid), `name` (string), `currency` (string), `balance` (number), `status` (string enum `[active, suspended, closed]`), `createdAt` (string/date-time) — all required
  - [ ] 400 response references `ErrorResponse`
  - [ ] 401 response references `ErrorResponse`
  - [ ] 403 response references `ErrorResponse`
- [ ] `GET /api/v1/accounts` defined with `operationId: listAccounts`
  - [ ] Query parameter `userId`: type string, format uuid, required
  - [ ] 200 response schema `AccountListResponse`: property `accounts` (array of `AccountResponse`), required
  - [ ] 401 response references `ErrorResponse`
- [ ] `ErrorResponse` schema: `status` (integer), `error` (string), `details` (array of string) — all required; matches shape in `user-openapi.yaml`
- [ ] All schemas defined under `components/schemas`
- [ ] File is valid OpenAPI 3.0.3

**Depends on:** none

---

## CONTROLLER Layer

### CONTROLLER-1 — LedgerApiDelegateImpl

**Layer:** Controller  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — step 12 (HTTP 201 on create); step 2 (HTTP 200 on list); implements generated `LedgerApiDelegate`  
**Inputs:**
- Generated `OpenAccountRequest` DTO — fields: `userId` (UUID), `currency` (String), `name` (String?)
- Generated `AccountResponse` DTO — fields: `accountId`, `name`, `currency`, `balance`, `status`, `createdAt`
- Generated `AccountListResponse` DTO — field: `accounts: List<AccountResponse>`
- `userId` query parameter for the list operation

**Outputs:**
- `org.dpp.tradelab.ledger.controller.LedgerApiDelegateImpl` — implements generated `LedgerApiDelegate`
- `ResponseEntity<AccountResponse>` — HTTP 201 on successful open
- `ResponseEntity<AccountListResponse>` — HTTP 200 on list
- Error responses handled by `GlobalExceptionHandler`: HTTP 400 (`InvalidCurrencyException`), HTTP 403 (`UserNotFoundException`), HTTP 401 (unauthenticated)

**Acceptance criteria:**
- [ ] Class is in `org.dpp.tradelab.ledger.controller`, annotated `@Service`, implements the generated `LedgerApiDelegate`
- [ ] `openAccount(request: OpenAccountRequest)` — converts `request.currency` string to `Currency` enum (throws `InvalidCurrencyException` if conversion fails), calls `accountService.openAccount(userId, currency, name)`, maps the returned `Account` to `AccountResponse`, returns `ResponseEntity.status(201).body(...)`
- [ ] `listAccounts(userId: UUID)` — calls `accountService.listAccountsByUser(userId)`, maps to `AccountListResponse`, returns `ResponseEntity.ok(...)`
- [ ] Never calls a repository directly
- [ ] No business logic — only DTO mapping and service delegation
- [ ] MockMvc tests cover:
  - [ ] `POST /api/v1/accounts` happy path → 201 with correct body
  - [ ] `POST /api/v1/accounts` with unsupported currency → 400
  - [ ] `POST /api/v1/accounts` with inactive user → 403
  - [ ] `GET /api/v1/accounts?userId=...` with accounts → 200 with list
  - [ ] `GET /api/v1/accounts?userId=...` with no accounts → 200 with empty list

**Depends on:** SVC-1, SVC-2, API-CONTRACT-1

---

## CLI Layer

### CLI-1 — Ledger API client: createAccount and fetchAccounts

**Layer:** API Client  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — steps 7–8 (submit → POST); step 2 (load list → GET)  
**Inputs:**
- `services/contract/ledger-openapi.yaml` — sole source of truth for URLs, methods, and shapes
- Shared Axios instance from `shared/api/`

**Outputs:**
- `services/front-end/src/domains/ledger/api/accountApi.ts`
- `services/front-end/src/domains/ledger/types/account.ts` — TypeScript interfaces: `OpenAccountRequest`, `AccountResponse`, `AccountListResponse`

**Acceptance criteria:**
- [ ] `accountApi.ts` imports only from the shared Axios instance — no new Axios instance created
- [ ] `createAccount(request: OpenAccountRequest): Promise<AccountResponse>` — `POST /api/v1/accounts`, returns typed response body
- [ ] `fetchAccounts(userId: string): Promise<AccountListResponse>` — `GET /api/v1/accounts?userId={userId}`, returns typed response body
- [ ] `OpenAccountRequest`: `userId: string`, `currency: 'USD' | 'GBP' | 'EUR'`, `name?: string`
- [ ] `AccountResponse`: `accountId: string`, `name: string`, `currency: string`, `balance: number`, `status: string`, `createdAt: string`
- [ ] `AccountListResponse`: `accounts: AccountResponse[]`
- [ ] `ACCOUNTS_QUERY_KEY` exported as a constant — used as the TanStack Query cache key base
- [ ] Unit tests mock Axios and assert:
  - [ ] `createAccount` calls `POST /api/v1/accounts` with the correct body
  - [ ] `fetchAccounts` calls `GET /api/v1/accounts` with `userId` as a query parameter

**Depends on:** API-CONTRACT-1

---

## STATE Layer

### STATE-1 — useLedger hooks: useAccounts and useOpenAccount

**Layer:** State  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — step 2 (load accounts list); steps 7–14 (submit, loading state, refresh list, dismiss form)  
**Inputs:**
- `createAccount`, `fetchAccounts` from CLI-1
- `ACCOUNTS_QUERY_KEY` from CLI-1
- `userId` from the Zustand session store

**Outputs:**
- `services/front-end/src/domains/ledger/hooks/useLedger.ts`
  - `useAccounts()` — TanStack Query `useQuery` hook
  - `useOpenAccount()` — TanStack Query `useMutation` hook

**Acceptance criteria:**
- [ ] `useAccounts()` reads `userId` from the Zustand session store; query key is `[ACCOUNTS_QUERY_KEY, userId]`; calls `fetchAccounts(userId)`
- [ ] `useAccounts()` is disabled (`enabled: false`) when `userId` is absent from the session store
- [ ] `useOpenAccount()` calls `createAccount(request)` on mutation; on success, invalidates `[ACCOUNTS_QUERY_KEY, userId]` so the list refreshes automatically (flow step 13)
- [ ] `useOpenAccount()` exposes `isPending` (TanStack Query v5) to allow the form to disable its submit button (flow step 8)
- [ ] Neither hook contains business logic — only query/mutation wiring
- [ ] Hook tests use `renderHook` and cover:
  - [ ] `useAccounts` returns account list on success
  - [ ] `useAccounts` returns empty array when backend returns empty list
  - [ ] `useAccounts` is disabled when `userId` is absent
  - [ ] `useOpenAccount` calls `createAccount` and invalidates cache on success
  - [ ] `useOpenAccount` exposes error state on API failure

**Depends on:** CLI-1

---

## COMP Layer

### COMP-1 — AccountList component

**Layer:** Component  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — step 2 (render list of existing accounts, including empty state)  
**Inputs:**
- `AccountListProps`: `accounts: AccountResponse[]`

**Outputs:**
- `services/front-end/src/domains/ledger/components/AccountList.tsx`

**Acceptance criteria:**
- [ ] Renders a list; each row displays: `name`, `currency`, `balance` (formatted to 2 decimal places), `status`, `createdAt` (formatted as local date)
- [ ] When `accounts` is an empty array, renders empty-state message: `"No accounts yet. Open one to get started."`
- [ ] No API calls or hook calls inside the component — data passed via props only
- [ ] Explicit `AccountListProps` TypeScript interface
- [ ] Component tests cover:
  - [ ] List with one or more accounts renders all rows with correct field values
  - [ ] Empty array renders the empty-state message

**Depends on:** CLI-1 (for `AccountResponse` type)

---

### COMP-2 — OpenAccountForm component

**Layer:** Component  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — steps 4–8; error cases "No currency selected", "Unsupported currency", "User not active"  
**Inputs:**
- `OpenAccountFormProps`:
  - `onSubmit: (currency: 'USD' | 'GBP' | 'EUR', name?: string) => void`
  - `isLoading: boolean`
  - `error?: string`
  - `onCancel: () => void`

**Outputs:**
- `services/front-end/src/domains/ledger/components/OpenAccountForm.tsx`

**Acceptance criteria:**
- [ ] Renders a currency selector with options `USD`, `GBP`, `EUR` — no pre-selected default
- [ ] Renders an optional name text input
- [ ] Renders an "Open account" submit button; when `isLoading` is `true` the button is disabled and shows a loading indicator (flow step 8)
- [ ] Renders a "Cancel" button that calls `onCancel`
- [ ] Client-side validation on submit: if no currency is selected, displays inline error `"Please select a base currency."` and does not call `onSubmit` (flow error case "No currency selected")
- [ ] When `error` prop is non-empty, displays it as a form-level error message above the submit button
- [ ] No API calls or state store access inside the component
- [ ] Explicit `OpenAccountFormProps` TypeScript interface
- [ ] Component tests cover:
  - [ ] Submit with no currency selected shows validation error and does not call `onSubmit`
  - [ ] Submit with valid currency calls `onSubmit` with correct arguments
  - [ ] `isLoading=true` disables the submit button
  - [ ] Non-empty `error` prop renders the error message
  - [ ] Cancel button calls `onCancel`

**Depends on:** none

---

## SCREEN Layer

### SCREEN-1 — AccountsPage screen

**Layer:** Screen  
**Domain:** ledger  
**Use case:** open-account  
**Implements:** open-account flow — steps 1–14 in full; user-session Flow A step 3 (Accounts sidebar link)  
**Inputs:**
- `useAccounts()` hook from STATE-1
- `useOpenAccount()` hook from STATE-1
- `AccountList` component from COMP-1
- `OpenAccountForm` component from COMP-2
- `userId` from Zustand session store
- Route: `/accounts`

**Outputs:**
- `services/front-end/src/domains/ledger/pages/AccountsPage.tsx`
- Route entry in `services/front-end/src/app/router.tsx`: `/accounts` → `AccountsPage` (authenticated route — redirects to `/login` if no session)
- Accounts nav link added to the Sidebar component in the shell layout (user-session Flow A, step 3)

**Acceptance criteria:**
- [ ] Page renders at `/accounts`
- [ ] Redirects to `/login` if no active session is present in the Zustand store (flow error case: unauthenticated)
- [ ] On load, calls `useAccounts()` and passes the result to `AccountList` (flow step 2)
- [ ] Shows a loading indicator while `useAccounts()` is fetching
- [ ] "Open new account" button is visible (flow step 2)
- [ ] Clicking "Open new account" reveals `OpenAccountForm` (flow steps 3–4)
- [ ] On form submit, calls `useOpenAccount().mutate(...)` with `userId` from session store, `currency`, and optional `name` (flow steps 7–8)
- [ ] `isLoading` / `isPending` from `useOpenAccount()` is passed to `OpenAccountForm` as `isLoading` prop (flow step 8)
- [ ] On mutation success: `OpenAccountForm` is dismissed (flow step 14); account list refreshes automatically via TanStack Query cache invalidation (flow step 13)
- [ ] On mutation error (HTTP 400 / 403): the error message string is passed to `OpenAccountForm` via the `error` prop
- [ ] On HTTP 401 response: frontend redirects to `/login`
- [ ] Sidebar component includes an Accounts nav link pointing to `/accounts`, visible only when a session exists (user-session Flow A step 3)
- [ ] Page tests cover:
  - [ ] Renders account list when accounts exist
  - [ ] Renders empty-state message when accounts list is empty
  - [ ] "Open new account" button reveals the form
  - [ ] Successful submission dismisses the form
  - [ ] API error from mutation renders in the form's `error` prop

**Depends on:** STATE-1, COMP-1, COMP-2
