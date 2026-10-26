# Decision: Pessimistic write locking for account balance mutations

**Date:** 2026-10-26
**Status:** accepted

## Context

GitHub issue #232 (Critical — financial integrity): `Account` had no concurrency
control. Balance mutations used an unlocked read-modify-write pattern in three places:

- `LedgerService.recordTransaction` (DEBIT / CASH) — read balance, subtract, save.
- `AccountService.topUpAccount` — read balance, add, save.
- `StockTradingService.placeBuyOrder` — fund check against an `AccountSummary.balance`
  snapshot captured at the start of the request and never re-read under a lock.

Two concurrent debits (or a debit + top-up) could both read the same balance and the
last write would win, losing an update. Two concurrent buys could each individually pass
the fund check against the same snapshot and overspend the account.

## Decision

Serialize account balance mutations with **pessimistic write locking** (`SELECT ... FOR
UPDATE`), not optimistic `@Version`. Pessimistic locking makes concurrent writers block
and re-read committed state rather than fail with an optimistic-lock exception requiring
retry logic. No `@Version` column is added.

1. `AccountRepository` gains a locked query `findByIdForUpdate` annotated
   `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
2. `LedgerService.recordTransaction` loads the account via `findByIdForUpdate` on the
   `DEBIT / CASH` balance-mutating branch, re-reading and re-checking the balance under
   the lock before debiting. Non-mutating entry types keep the plain existence check.
3. `AccountService.topUpAccount` loads the account via `findByIdForUpdate` before adding.
4. Stock buys are settled by a single cohesive Ledger operation,
   `LedgerApi.recordStockBuy(accountId, userId, ticker, quantity, executionPrice,
   currency): StockBuyResult`. The Ledger takes the pessimistic lock as it begins writing,
   performs the fund check under that lock against committed state, and either writes the
   `DEBIT / CASH` + `CREDIT / STOCK_BUY` entries and debits the balance
   (`StockBuyResult.COMPLETED`) or writes nothing (`StockBuyResult.INSUFFICIENT_FUNDS`).
   `StockTradingService.placeBuyOrder` maps the result to `FILLED` / `REJECTED`. No
   balance read or locking primitive is exposed on the cross-domain api, and a losing
   concurrent buy is cleanly `REJECTED` (HTTP 200) with no ledger entries — not a rollback.
   This realigns the code with decision `2026-07-08-stock-trading-ledger-sync-api`, which
   already specified a single `recordStockBuy` operation.

## Consequences

- Concurrent balance mutations on the same account are serialized; no update is lost and
  the fund check reflects committed state (issue #232 "Expected").
- The pessimistic lock works on both H2 (dev/test) and PostgreSQL (prod).
- The Ledger's cross-domain `api/` contract exposes only domain-meaningful operations
  (`recordTransaction`, `getBalance`, `recordStockBuy`) — no locking or balance-for-update
  primitive leaks to consumers.
- `recordStockBuy` runs `@Transactional` (not read-only) and participates in the caller's
  transaction via default `REQUIRED` propagation, so the lock is held for the duration of
  the stock-trading transaction.
- Stock Trading no longer orchestrates cash debits / stock credits or performs a balance
  fund check; it places the order and reacts to the Ledger's result.
- The existing negative-balance `IllegalStateException` guard in `recordTransaction`
  remains as a defence-in-depth backstop for the generic path (now used by the sell flow).
- No `@Version` field and no schema change to `Account`.

## Alternatives considered

- **Optimistic locking (`@Version`):** rejected. Concurrent writers would receive
  `ObjectOptimisticLockingFailureException` and require retry/backoff logic; it aborts
  rather than serializes, giving a worse fit for "serialized so no update is lost."
- **Locked balance read on the api (`getBalanceForUpdate`) used by a fund check in Stock
  Trading:** rejected. It leaked a locking/concurrency primitive onto the cross-domain
  contract and placed the fund rule outside the domain that owns the money.

## Known pre-existing issue (not addressed here)

`LedgerService.recordTransaction` updates `Account.balance` only on `DEBIT / CASH`; it
does not increment the stored balance on `CREDIT / CASH` (sell proceeds). That is a
separate, pre-existing correctness gap unrelated to concurrency and is out of scope for
issue #232.
