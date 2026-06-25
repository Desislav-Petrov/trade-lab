# Use Case: Manage Asset Subscriptions

## Goal

An authenticated user manages their stock ticker watchlist — viewing current subscriptions, adding new ones from the supported list, and removing existing ones — all from the Stock Trading page.

## Actor

Authenticated User — a logged-in user with an active session in the Zustand store.

## Screen

- **Route:** `/trade`
- **Page:** `StockTradingPage`
- **Entry point:** Authenticated user clicks the Stock Trading link in the sidebar.

## Trigger

Authenticated user navigates to `/trade`.

## Domain Models

- `domain/model/asset-subscription`
- `domain/model/session`

## Flows

- `domain/flows/manage-asset-subscriptions` (Flow A — Load Subscriptions)
- `domain/flows/manage-asset-subscriptions` (Flow B — Bulk Add Subscriptions)
- `domain/flows/manage-asset-subscriptions` (Flow C — Bulk Remove Subscriptions)

## Happy Path

1. Authenticated user clicks Stock Trading in the sidebar.
2. Frontend calls `GET /api/v1/market-data/subscriptions`; backend returns the user's current subscription list (ticker + companyName per entry).
3. Page renders the subscription list. If empty, an empty-state message is shown.
4. User clicks "Add tickers"; the add panel opens showing all supported tickers not yet subscribed, with a real-time client-side filter input.
5. User types to filter and selects one or more tickers; clicks "Add".
6. Frontend calls `POST /api/v1/market-data/subscriptions`; backend creates the records and returns HTTP 201.
7. Page updates the subscription list with the newly added tickers without a full reload. Add panel closes.
8. User selects one or more subscriptions via checkboxes; clicks "Remove".
9. Frontend calls `DELETE /api/v1/market-data/subscriptions`; backend deletes the records and returns HTTP 204.
10. Page removes the deleted entries from the list without a full reload. If none remain, the empty-state message is shown.

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| No active session | Redirect to `/login`. |
| Add: ticker already subscribed | HTTP 409 — error message shown; no subscriptions created. |
| Add: ticker not in supported list | HTTP 400 — error message shown; no subscriptions created. |
| Add: subscription limit (1000) would be exceeded | HTTP 422 — message: "Adding these tickers would exceed your 1000 subscription limit." |
| Remove: ticker not found in user's subscriptions | HTTP 404 — error message shown; no records deleted. |
| Server error on any operation | HTTP 500 — generic error message shown; no state changes applied. |

## Out of Scope

- Real-time price data or quotes on the subscription list.
- Reordering subscriptions.
- Subscription categories or grouping.
- Notifications or alerts triggered by subscriptions.
- Exporting the subscription list.
- Any trading action (buy/sell) initiated from this page.
