# Decision: Rename Trade nav item to Stock Trading and introduce AssetSubscription entity

**Date:** 2026-06-25  
**Status:** accepted

## Context

A new watchlist feature was introduced for the Stock Trading vertical. This required:

1. A new page scoped to stock trading — creating a naming conflict with the existing "Trade" sidebar nav item defined in `domain/flows/user-session.md`.
2. A new persistent entity to track per-user stock ticker subscriptions, which has no equivalent in the existing domain.

## Decision

### 1. Rename "Trade" to "Stock Trading" in the sidebar nav

The existing "Trade" nav label and its route are renamed to "Stock Trading" throughout the domain docs. The route `/trade` established by the login flow postcondition is preserved — only the display label changes.

### 2. Introduce AssetSubscription in the Market Data domain

A new `AssetSubscription` entity is created in the Market Data horizontal domain. It tracks a single user's subscription to a single ticker symbol. The full set of supported tickers is provided as a static configuration file in the backend resources folder (4-letter ticker + company name). Subscriptions are managed per user via REST APIs.

This entity belongs to Market Data (not Stock Trading) because subscription management is a data-sourcing concern — it tracks what instruments a user wants to watch, independent of any trading action.

## Consequences

- `domain/flows/user-session.md` is updated: "Trade" is replaced with "Stock Trading" in sidebar nav references in Flows A and C.
- `domain/model/asset-subscription.md` is created as a new entity.
- `domain/flows/manage-asset-subscriptions.md` is created covering load, bulk add, and bulk remove.
- `domain/usecases/manage-asset-subscriptions.md` is created as the multi-flow use case for the Stock Trading page.
- Any future docs or implementation referencing the sidebar nav must use "Stock Trading" — not "Trade".
- The supported tickers list is static config — it is not a database entity and must not be modelled as one.
