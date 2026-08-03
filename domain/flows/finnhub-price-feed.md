# Finnhub Price Feed

## Overview

Describes the lifecycle of the `FinnhubPriceFeedAdapter` — the real market data feed component within the Market Data domain. The adapter polls the Finnhub `/quote` REST endpoint once per second, cycling through the supported ticker universe in round-robin order. Each successful response is mapped to a `MarketDataSnapshot` cache entry and enriched with the company name from the in-memory symbol → company name lookup. Failed calls are silently dropped. The adapter runs continuously from application startup alongside the synthetic feed.

The Finnhub OpenAPI client is generated from the Finnhub Swagger spec stored in `services/contract/finnhub-openapi.yaml`. The API key is supplied via the `X-Finnhub-Token` request header and is configured as an environment-level Spring application property (`finnhub.api-key`). No server-side stubs are generated — the generated artefact is a client only.

Rate limit: 60 requests per minute / 30 API calls per second (free tier). The one-per-second polling cadence stays comfortably within these limits.

---

## Flow A — Startup Initialisation

The adapter initialises its round-robin index and the in-memory symbol → company name lookup at application startup, before the scheduler begins polling.

### Actors

- **System (Market Data)**: `FinnhubPriceFeedAdapter` on Spring context startup.

### Preconditions

- The supported universe resources file is present on the classpath.
- The Spring application context is starting up.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Market Data) | Load supported universe | Reads the supported universe resources file from the classpath. Parses each entry as a `ticker → companyName` mapping. |
| 2 | System (Market Data) | Build company name lookup | Populates the in-memory `Map<String, String>` (ticker → companyName). This map is immutable at runtime. |
| 3 | System (Market Data) | Initialise round-robin index | Sets the round-robin cursor to position 0 over the ordered list of supported tickers. |

### Postconditions

- The in-memory company name lookup is fully populated.
- The round-robin cursor is at position 0.
- The scheduler is ready to begin polling.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Resources file missing | Supported universe file not found on classpath | Application fails to start. ERROR logged. |

---

## Flow B — Scheduled Tick (steady state)

The Spring `@Scheduled` component fires once per second. Each invocation polls Finnhub for the next ticker in round-robin order, maps the response, enriches with company name, and writes the result to the shared `MarketDataSnapshot` cache.

### Actors

- **System (Market Data)**: The `@Scheduled` method on `FinnhubPriceFeedAdapter`.

### Preconditions

- Flow A has completed successfully.
- The Spring scheduler is running.
- A valid Finnhub API key is configured.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Market Data) | Advance round-robin cursor | Reads the current cursor position. Selects `tickers[cursor]` as the target ticker. Increments cursor; wraps to 0 when the end of the list is reached. |
| 2 | System (Market Data) | Call Finnhub `/quote` | Issues a GET request to `/quote?symbol={ticker}` with the `X-Finnhub-Token` header set to the configured API key. |
| 3 | System (Market Data) | Map response fields | On HTTP 200: extracts fields from the response body and maps them as follows: `c` → `currentPrice`, `o` → `open`, `h` → `dayHigh`, `l` → `dayLow`, `h` → `fiftyTwoWeekHigh`. All values are rounded to 3 decimal places. |
| 4 | System (Market Data) | Enrich with company name | Looks up `companyName` in the in-memory symbol → company name map using the ticker as the key. |
| 5 | System (Market Data) | Write to snapshot cache | Overwrites the `MarketDataSnapshot` cache entry for the ticker with the mapped and enriched values. Sets `updatedAt` to the current timestamp. |
| 6 | System (Market Data) | Trigger tick dispatch | Notifies the WebSocket dispatch component that a new tick is available for the ticker. The dispatch component forwards the tick to all connected users with `feedType = REAL` who are subscribed to that ticker (see `domain/flows/market-data-websocket-feed.md` Flow B). |

### Postconditions

- The `MarketDataSnapshot` cache entry for the polled ticker reflects the latest Finnhub data.
- All connected `REAL` feed subscribers for that ticker receive a `TICK` WebSocket message.
- The round-robin cursor has advanced by one position (wrapping if necessary).

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Finnhub API call fails | Any non-200 HTTP status, network timeout, or client exception | Tick is silently dropped. Cache entry for the ticker is not modified. Cursor still advances. No error is propagated. |
| Ticker not in company name map | Ticker present in universe file but missing from lookup (should not occur post-startup) | `companyName` is set to an empty string. Tick is still written to cache and dispatched. WARN logged. |

---

## Flow C — Graceful Shutdown

The scheduler stops polling when the Spring application context shuts down. No in-flight requests are retried.

### Actors

- **System (Market Data)**: Spring scheduler lifecycle management.

### Preconditions

- The Spring application context is shutting down.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | System (Market Data) | Stop scheduler | Spring destroys the scheduled task. No further invocations of the polling method occur. |
| 2 | System (Market Data) | Discard in-flight call | Any Finnhub API call in progress at shutdown time is abandoned. Its result is not written to the cache. |

### Postconditions

- No further Finnhub API calls are made.
- The snapshot cache retains whatever values were last written. It is not cleared on shutdown.

---

## Field Mapping — Finnhub `/quote` Response

| Finnhub field | Description | Maps to `MarketDataSnapshot` field |
|---|---|---|
| `c` | Current price | `currentPrice` |
| `o` | Open price of the day | `open` |
| `h` | High price of the day | `dayHigh` |
| `l` | Low price of the day | `dayLow` |
| `h` | High price of the day (reused) | `fiftyTwoWeekHigh` |
| `d` | Change | _ignored_ |
| `dp` | Percent change | _ignored_ |
| `pc` | Previous close | _ignored_ |

---

## Configuration

| Property | Description |
|---|---|
| `finnhub.api-key` | API key passed as `X-Finnhub-Token` header on every request. Required. Sourced from environment. |

---

## Domain Models Involved

- **MarketDataSnapshot**: Written on every successful Finnhub tick (Flow B step 5). The shared cache is also read by the WebSocket dispatch component.
- **AssetSubscription**: Consulted by the WebSocket dispatch component (not by this adapter directly) to determine which connected users should receive the tick.
- **UserSettings**: Feed-type routing cache consulted by the WebSocket dispatch component to filter tick delivery to `REAL` feed users only.
