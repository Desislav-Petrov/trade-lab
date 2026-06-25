# Decision: Supported tickers list delivery to the frontend

**Date:** 2026-06-25  
**Status:** superseded by `2026-06-25-supported-tickers-api-endpoint.md`

## Context

`tasks/manage-asset-subscriptions.md` (SCREEN-1) identified a blocker: the `AddTickerPanel` component needs the full list of supported stock tickers (ticker + company name) to present to the user. The existing `GET /api/v1/market-data/subscriptions` endpoint returns only the authenticated user's own subscriptions — not the full supported list.

Two options were considered:

1. **New backend endpoint** `GET /api/v1/market-data/supported-tickers` — returns the full supported list from the server at request time. Requires API-CONTRACT amendment, new service method, new controller method, new CLI function, and a new `useQuery` hook.

2. **Static frontend asset** — copy `supported-tickers.csv` (already committed to backend resources) into the frontend's `public/` folder. The frontend loads it once at startup (or lazily on first render) and parses it client-side. No new endpoint required.

## Decision

~~Option 2 — static frontend asset.~~ **Superseded.**

This decision was reversed during PR review. Duplicating the configuration across backend resources and frontend public assets was identified as a maintenance problem. See `decisions/2026-06-25-supported-tickers-api-endpoint.md` for the accepted replacement decision.
