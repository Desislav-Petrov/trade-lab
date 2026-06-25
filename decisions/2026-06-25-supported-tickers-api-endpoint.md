# Decision: Serve supported tickers list via backend API endpoint

**Date:** 2026-06-25  
**Status:** accepted  
**Supersedes:** `2026-06-25-supported-tickers-static-asset.md`

## Context

The initial implementation of the stock watchlist feature served the supported tickers list as a static CSV file in the frontend `public/` folder. During PR review this was rejected because it duplicates the configuration — the same CSV already lives in the backend resources folder (`services/backend/src/main/resources/supported-tickers.csv`). Duplicated configuration creates a maintenance risk: the two copies can drift out of sync silently.

## Decision

Add `GET /api/v1/market-data/supported-tickers` to the Market Data backend. The endpoint reads from `SupportedTickerConfig` (which owns the single authoritative copy of the CSV) and returns the full sorted list as `SubscriptionResponse[]`. The frontend fetches this endpoint once on session login and caches the result for the duration of the session using TanStack Query with `staleTime: Infinity`.

`SupportedTickerConfig` is moved from `org.dpp.tradelab.marketdata.service` to `org.dpp.tradelab.marketdata.config` to reflect that it is configuration, not business logic.

## Consequences

- `services/contract/marketdata-openapi.yaml` gains a `GET /market-data/supported-tickers` path with `operationId: getSupportedTickers`.
- `AssetSubscriptionService.getSupportedTickers()` is added — returns sorted ticker/companyName pairs from `SupportedTickerConfig.getAll()`.
- `MarketDataApiDelegateImpl.getSupportedTickers()` is added — delegates to the service and maps to `SubscriptionResponse[]`.
- `SupportedTickerConfig` is moved from `marketdata.service` to `marketdata.config`; all imports updated accordingly.
- `services/front-end/public/supported-tickers.csv` is deleted — the frontend no longer holds a copy.
- `useSupportedTickers` hook is changed from a raw `fetch`/CSV-parse implementation to a `useQuery` wrapping `fetchSupportedTickers`. The query is cached with `staleTime: Infinity` so it is only fetched once per session.
- The supported tickers list has a single source of truth: `services/backend/src/main/resources/supported-tickers.csv`.
