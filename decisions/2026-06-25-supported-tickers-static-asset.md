# Decision: Supported tickers list delivery to the frontend

**Date:** 2026-06-25  
**Status:** accepted

## Context

`tasks/manage-asset-subscriptions.md` (SCREEN-1) identified a blocker: the `AddTickerPanel` component needs the full list of supported stock tickers (ticker + company name) to present to the user. The existing `GET /api/v1/market-data/subscriptions` endpoint returns only the authenticated user's own subscriptions — not the full supported list.

Two options were considered:

1. **New backend endpoint** `GET /api/v1/market-data/supported-tickers` — returns the full supported list from the server at request time. Requires API-CONTRACT amendment, new service method, new controller method, new CLI function, and a new `useQuery` hook.

2. **Static frontend asset** — copy `supported-tickers.csv` (already committed to backend resources) into the frontend's `public/` folder. The frontend loads it once at startup (or lazily on first render) and parses it client-side. No new endpoint required.

## Decision

Option 2 — static frontend asset.

The supported tickers list is static configuration that does not change at runtime. It is not user-specific and has no security sensitivity. Serving it as a static file via the frontend build tool (Vite) is simpler, removes an unnecessary HTTP round-trip on page load, and requires no API-CONTRACT or backend changes. The frontend reads `public/supported-tickers.csv` once via `fetch` (or imports it as a module) and parses it client-side.

A `useSupportedTickers` hook in `domains/marketdata/hooks/` owns the load and parse logic, keeping components free of raw fetch calls.

## Consequences

- `supported-tickers.csv` is also placed at `services/front-end/public/supported-tickers.csv`.
- A `useSupportedTickers` hook is added to `services/front-end/src/domains/marketdata/hooks/` — returns `SubscriptionResponse[]` (ticker + companyName pairs) parsed from the CSV.
- `SCREEN-1` is unblocked. `StockTradingPage` passes `useSupportedTickers` data minus current subscriptions to `AddTickerPanel`.
- No new backend endpoint is added for this use case. If the ticker list ever needs to become dynamic (e.g. admin-managed), a new decision log entry must be created and the backend endpoint approach revisited.
- `tasks/manage-asset-subscriptions.md` SCREEN-1 note is superseded by this decision.
