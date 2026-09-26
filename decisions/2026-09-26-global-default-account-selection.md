# Decision: Default Account Selection Seeded Globally for the Authenticated Session

**Date:** 2026-09-26
**Status:** accepted

## Context

The AI assistant (`agent` domain) is mounted globally in `App.tsx` for every
authenticated session. It resolves the account to chat about by reading
`selectedAccountId` from the shared `useSelectedAccountStore`.

However, the "default account" (first active account) was only ever resolved
inside a `useEffect` in `PortfolioPage`. That effect runs solely when the
Portfolio page is mounted. As a result, a user who opened the assistant on any
route before first visiting `/portfolio` saw "no account selected", even though
Portfolio would later show a default account. This produced the reported bug:
the assistant reported no account while Portfolio had one selected.

## Decision

Default-account resolution is lifted out of `PortfolioPage` into an app-level,
page-independent mechanism:

- `useDefaultAccountSelection` (shared hook) reads the active accounts list from
  the `ledger` TanStack Query hook and seeds `useSelectedAccountStore` with the
  first active account when no account is currently selected.
- `AccountSelectionBootstrap` (renderless shared component) mounts the hook once
  at the app root, inside the existing authenticated-session gate, so the
  default is available on every page — not only Portfolio.

The shared `useSelectedAccountStore` remains the single source of truth. Only
the account `id` is stored; the full account object stays in the ledger TanStack
Query cache. `PortfolioPage`'s own defaulting effect is retained as a harmless
fallback and continues to reconcile through the existing store subscriptions.

## Consequences

- Any globally-mounted consumer of the selected account (today: the AI
  assistant) sees a sensible default for the whole session without depending on
  the Portfolio page having been visited.
- The active-accounts query now runs for authenticated sessions regardless of
  route; it shares the existing `ledger` query key, so there is no duplicate
  network cost beyond the first fetch.
- If a user has no active accounts, nothing is seeded and consumers correctly
  reflect "no account selected".
