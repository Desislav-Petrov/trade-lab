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
4. The Ledger `api/` contract gains `LedgerApi.getBalanceForUpdate(accountId):
   AccountBalanceResult`, a pessimistic-locked balance read that participates in the
   caller's transaction (propagation REQUIRED, not read-only). `StockTradingService`'s
   buy-flow fund check calls it instead of using the request-start snapshot. Because the
   lock is held until the surrounding `placeOrder` transaction commits, a losing
   concurrent buy blocks, then reads the committed (already-debited) balance and is
   cleanly transitioned to `REJECTED` (HTTP 200) per the buy-stock flow.

## Consequences

- Concurrent balance mutations on the same account are serialized; no update is lost and
  the fund check reflects committed state (issue #232 "Expected").
- The pessimistic lock works on both H2 (dev/test) and PostgreSQL (prod).
- `getBalanceForUpdate` must run in a writable transaction; it is `@Transactional` (not
  read-only) and relies on default `REQUIRED` propagation so the lock is held for the
  duration of the stock-trading transaction.
- The existing negative-balance `IllegalStateException` guard in `recordTransaction`
  remains as a defence-in-depth backstop.
- No `@Version` field and no schema change to `Account`.
- When Ledger is extracted into a standalone service, the in-process lock becomes a lock
  held by the Ledger service's own transaction; the `LedgerApi` contract is unchanged.

## Alternatives considered

- **Optimistic locking (`@Version`):** rejected. Concurrent writers would receive
  `ObjectOptimisticLockingFailureException` and require retry/backoff logic; it aborts
  rather than serializes, giving a worse fit for "serialized so no update is lost."

## Known pre-existing issue (not addressed here)

`LedgerService.recordTransaction` updates `Account.balance` only on `DEBIT / CASH`; it
does not increment the stored balance on `CREDIT / CASH` (sell proceeds). That is a
separate, pre-existing correctness gap unrelated to concurrency and is intentionally out
of scope for issue #232.
