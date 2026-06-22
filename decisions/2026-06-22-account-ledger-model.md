# Decision: Introduce Ledger Entry model and revise Account entity

**Date:** 2026-06-22  
**Status:** accepted

## Context

The original `domain/model/account.md` modelled balance as a single mutable `decimal` field updated in place on every top-up. It contained a `TopUp` behavior and emitted an `AccountToppedUp` event directly on the `Account` entity.

The open-account use case introduced two new requirements that conflict with this model:

1. Every debit and credit across all instruments (cash today, stock holdings in future) must be recorded as a discrete, immutable transaction entry — giving the account a full audit history.
2. The account must support multiple asset types per account, not just a single cash balance.

A pure stored-balance model cannot satisfy requirement 1 without duplicating state. Replacing it entirely with a derived-balance (sum-of-entries) model was considered but rejected because it introduces query complexity and makes balance reads expensive.

## Decision

Keep the stored `balance` field on `Account` as the authoritative source of available cash in the account's base currency. Introduce a new `LedgerEntry` entity that records every credit and debit event as an immutable append-only row. The stored balance is always kept in sync with the ledger: every operation that mutates balance must also append a corresponding `LedgerEntry`.

The `TopUp` behavior and `AccountToppedUp` event are removed from `account.md` — they belong to the future top-up use case and will be specified there. The `Account` entity now carries only the `Open` behavior.

## Consequences

- `domain/model/account.md` is updated: `name` field added, `TopUp` behavior removed, `Open` behavior added, `LedgerEntry` relationship added, business rules updated.
- `domain/model/ledger-entry.md` is created as a new entity.
- Any future flow that moves funds (top-up, trade fill, withdrawal) must append a `LedgerEntry` row in addition to updating `Account.balance`.
- The top-up use case (separate, not yet specified) must follow the same pattern.
