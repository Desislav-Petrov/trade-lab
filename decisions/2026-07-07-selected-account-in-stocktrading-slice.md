# Decision: Selected Trading Account Stored in stocktrading Zustand Slice

**Date:** 2026-07-07
**Status:** accepted

## Context

Issue #38 introduces an account selector on the `/trade` page. The user's selected funding account must be held in frontend client state (Zustand) for the duration of the browser session. The `Account` entity belongs to the `ledger` domain — it is defined there and served by the ledger API. This raises the question of which Zustand slice should own the selection.

## Decision

The selected account ID is stored in the `stocktrading` Zustand slice, not the `ledger` slice.

The rationale: the selection is not a ledger concern. It is a piece of UI state that drives the upcoming stock-buy flow, which lives entirely in the `stocktrading` domain. The `ledger` domain's Zustand slice holds account list data (server state via TanStack Query); the *choice* of which account to trade from is a stocktrading UI concern. Keeping the selection in `stocktrading` avoids coupling the `stocktrading` domain to the `ledger` slice and makes the dependency explicit and unidirectional.

Only the account `id` is stored in the `stocktrading` slice — not the full account object. The full object remains in the TanStack Query cache owned by the `ledger` domain.

## Consequences

- The `stocktrading` Zustand slice must expose a `selectedAccountId: string | null` field and a setter.
- The account selector component reads the active accounts list from the `ledger` TanStack Query hook and reads/writes the selection via the `stocktrading` Zustand slice.
- The future stock-buy flow reads `selectedAccountId` from the `stocktrading` slice directly — no cross-slice dependency required.
- If the `ledger` domain is ever extracted into a standalone service, the `selectedAccountId` in `stocktrading` remains valid — it is a UUID reference, not a coupled object.
