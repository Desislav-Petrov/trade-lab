# Tasks: Implement Real Market Feed (Finnhub Integration)

**Source issue:** #44
**Domain docs:** `domain/flows/finnhub-price-feed.md`, `domain/flows/market-data-feed-routing.md`, `domain/flows/market-data-websocket-feed.md`, `domain/model/market-data-snapshot.md`
**Use case:** `view-profile` (FE settings toggle)
**Standards:** `standards/backend.md`, `standards/frontend.md`, `standards/architecture.md`

---

## Summary

Introduce a real market data feed sourced from Finnhub's `/quote` REST endpoint. The Finnhub client is generated from the Finnhub Swagger spec via the OpenAPI generator (client only, no server stubs). A `MarketDataFeedAdapter` interface abstracts both the existing synthetic feed and the new Finnhub feed. A Spring `@Scheduled` component polls Finnhub once per second in round-robin order over the supported ticker universe. The feed-type routing cache now delivers real data to `REAL` users instead of falling back to synthetic. On the frontend, the Platform Settings feed-type toggle is wired up to correctly send `REAL`/`SYNTHETIC` values and reflect the saved state.

---

## Backend Tasks

---

### BE-1 — Add Finnhub OpenAPI spec to contracts folder

**Layer:** API-CONTRACT (external client spec)
**Domain:** marketdata
**Use case:** implement-real-market-feed
**Implements:** `domain/flows/finnhub-price-feed.md` — Flow B step 2 (Finnhub `/quote` call)
**Inputs:**
- Finnhub public Swagger spec from https://finnhub.io/static/swagger.json
**Outputs:**
- `services/contract/finnhub-openapi.yaml` — trimmed OpenAPI 3.0.3 spec containing only the `/quote` endpoint and its response schema. Server URL: `https://finnhub.io/api/v1`. No server-side paths or tags beyond `/quote`.
**Acceptance criteria:**
- [ ] File exists at `services/contract/finnhub-openapi.yaml`
- [ ] Contains only the `/quote` GET endpoint with query param `symbol`
- [ ] Response schema includes fields: `c` (number), `h` (number), `l` (number), `o` (number), `d` (number), `dp` (number), `pc` (number)
- [ ] No server stubs or delegate patterns — client only
- [ ] File is valid OpenAPI 3.0.3 (parseable by openapi-generator)
**Depends on:** none

---

### BE-2 — Configure OpenAPI generator to produce Finnhub client

**Layer:** SVC (build configuration)
**Domain:** marketdata
**Use case:** implement-real-market-feed
**Implements:** `domain/flows/finnhub-price-feed.md` — Flow B step 2
**Inputs:**
- `services/contract/finnhub-openapi.yaml` (BE-1)
- `services/back-end/build.gradle.kts`
**Outputs:**
- New `openApiGenerate` task block in `build.gradle.kts` targeting `finnhub-openapi.yaml`
- Generated client classes at `org.dpp.tradelab.marketdata.generated.finnhub` on compile classpath
- `apiPackage`: `org.dpp.tradelab.marketdata.generated.finnhub.api`
- `modelPackage`: `org.dpp.tradelab.marketdata.generated.finnhub.model`
- Generator: `kotlin` (not `kotlin-spring`) — client only, no Spring server stubs
**Acceptance criteria:**
- [ ] `./gradlew openApiGenerate` succeeds without error
- [ ] `./gradlew build` compiles with the generated Finnhub client on the classpath
- [ ] Generated client provides a typed method to call `GET /quote?symbol=X`
- [ ] No `@RestController` or `ApiDelegate` artefacts generated for Finnhub
**Depends on:** BE-1

---

### BE-3 — Introduce `MarketDataFeedAdapter` interface

**Layer:** SVC
**Domain:** marketdata
**Use case:** implement-real-market-feed
**Implements:** `domain/model/market-data-snapshot.md` — Business Rules (adapter interface); `domain/flows/finnhub-price-feed.md` — overview
**Inputs:** none
**Outputs:**
- `org.dpp.tradelab.marketdata.service.MarketDataFeedAdapter` — Kotlin interface
- Single method: `fun start()` — called by the service layer to activate the adapter
- (Alternatively, the adapter can be a self-contained `@Component` that starts itself on `@PostConstruct`; either is acceptable as long as the service layer only references the interface)
**Acceptance criteria:**
- [ ] Interface exists in `marketdata.service` package
- [ ] No concrete implementation classes in `marketdata.service` — they go in `marketdata.service` but are named `SyntheticPriceFeedAdapter` and `FinnhubPriceFeedAdapter`
- [ ] Existing synthetic feed logic is extracted/renamed to `SyntheticPriceFeedAdapter` implementing `MarketDataFeedAdapter`
- [ ] `SyntheticPriceFeedAdapter` behaviour is unchanged: 1–10 random tickers every 250 ms
- [ ] Unit tests updated to reference `SyntheticPriceFeedAdapter` directly; no regression
**Depends on:** none

---

### BE-4 — Implement `FinnhubPriceFeedAdapter`

**Layer:** SVC
**Domain:** marketdata
**Use case:** implement-real-market-feed
**Implements:** `domain/flows/finnhub-price-feed.md` — Flow A (startup init), Flow B (scheduled tick), Flow C (shutdown)
**Inputs:**
- Generated Finnhub client (BE-2)
- `MarketDataFeedAdapter` interface (BE-3)
- Supported universe resources file (existing, on classpath)
- Spring config property `finnhub.api-key` (String)
**Outputs:**
- `org.dpp.tradelab.marketdata.service.FinnhubPriceFeedAdapter` — `@Component` implementing `MarketDataFeedAdapter`
- On `@PostConstruct`: loads supported universe file → builds `Map<String, String>` (ticker → companyName) → initialises round-robin cursor at 0
- `@Scheduled(fixedDelay = 1000)` method: advances round-robin cursor → calls Finnhub `/quote?symbol={ticker}` with `X-Finnhub-Token` header → maps response fields (`c`→`currentPrice`, `o`→`open`, `h`→`dayHigh`, `l`→`dayLow`, `h`→`fiftyTwoWeekHigh`) → enriches `companyName` from in-memory map → writes to shared `MarketDataSnapshot` cache → triggers tick dispatch for affected ticker
- On any Finnhub API failure: silently drops the tick, does not modify cache, cursor still advances
- `finnhub.api-key` wired via `@Value("${finnhub.api-key}")`
**Acceptance criteria:**
- [ ] `FinnhubPriceFeedAdapter` exists in `marketdata.service` and implements `MarketDataFeedAdapter`
- [ ] Round-robin cycles all tickers in order, wrapping to 0 at end of list
- [ ] All five fields mapped correctly from Finnhub response
- [ ] `fiftyTwoWeekHigh` is set equal to the `h` field (same as `dayHigh`)
- [ ] `companyName` enriched from in-memory map; missing key logs WARN and sets empty string
- [ ] Failed API call: cache entry unchanged, cursor advances, no exception propagated
- [ ] `finnhub.api-key` property is read from Spring config — not hardcoded
- [ ] Unit tests cover: successful tick mapping, API failure (silent drop), company name enrichment, round-robin wrap-around
**Depends on:** BE-2, BE-3

---

### BE-5 — Wire feed-type routing to deliver real data from Finnhub

**Layer:** SVC
**Domain:** marketdata
**Use case:** implement-real-market-feed
**Implements:** `domain/flows/market-data-feed-routing.md` — Flow C step 3b; `domain/flows/market-data-websocket-feed.md` — Flow B step 4
**Inputs:**
- Existing feed-type routing cache (`userId → feedType`)
- Existing WebSocket tick dispatch logic
- `FinnhubPriceFeedAdapter` writing to the shared `MarketDataSnapshot` cache (BE-4)
**Outputs:**
- Tick dispatch logic updated: ticks originating from `SyntheticPriceFeedAdapter` are dispatched **only** to users with `feedType = SYNTHETIC` (or cache miss). Ticks originating from `FinnhubPriceFeedAdapter` are dispatched **only** to users with `feedType = REAL`.
- The source adapter identity must be carried through to the dispatch decision. Acceptable approaches: each adapter sets a tag on the cache write, or the adapter calls a different dispatch method.
**Acceptance criteria:**
- [ ] A `SYNTHETIC` user receives only synthetic ticks; a `REAL` user receives only Finnhub ticks
- [ ] Cache miss on dispatch still falls back to `SYNTHETIC` and logs WARN
- [ ] No regression on existing `SYNTHETIC` feed path
- [ ] Unit tests cover: SYNTHETIC user receives synthetic tick, REAL user receives Finnhub tick, cache miss falls back to SYNTHETIC
**Depends on:** BE-4

---

### BE-6 — Add `finnhub.api-key` to `application.yml`

**Layer:** SVC (configuration)
**Domain:** marketdata
**Use case:** implement-real-market-feed
**Implements:** `domain/flows/finnhub-price-feed.md` — Configuration section
**Inputs:** none
**Outputs:**
- `services/back-end/src/main/resources/application.yml` updated with:
  ```yaml
  finnhub:
    api-key: ${FINNHUB_API_KEY:demo}
  ```
- Default value `demo` used when env var is absent (allows build/tests to pass without a real key)
**Acceptance criteria:**
- [ ] Property present in `application.yml`
- [ ] Application starts without error when `FINNHUB_API_KEY` env var is absent
- [ ] `FinnhubPriceFeedAdapter` reads the value correctly via `@Value`
**Depends on:** BE-4

---

## Frontend Tasks

---

### FE-1 — Wire feed-type toggle to send correct `REAL`/`SYNTHETIC` values

**Layer:** STATE
**Domain:** user
**Use case:** view-profile (`domain/usecases/view-profile.md`)
**Implements:** `domain/flows/update-user-settings.md` — steps 4, 12; `domain/flows/update-user-settings.md` — error cases
**Inputs:**
- Existing `PATCH /api/v1/users/{userId}/settings` API call in `user/api/`
- Existing Zustand session store `session.settings.feedType`
- Existing feed-type dropdown component on the Platform Settings tab
**Outputs:**
- `useMutation` hook (or existing hook updated) in `user/hooks/` that calls `PATCH /api/v1/users/{userId}/settings` with `{ feedType: "SYNTHETIC" | "REAL" }`
- On HTTP 200: updates `session.settings` in Zustand store with the returned `UserSettingsResponse`
- On HTTP 400/500: shows inline error on the dropdown; reverts dropdown to previous value
- On HTTP 401: redirects to `/login`
- On HTTP 403: shows generic error message
**Acceptance criteria:**
- [ ] Selecting "Real Market Feed" sends `{ "feedType": "REAL" }` to the backend
- [ ] Selecting "Synthetic Feed" sends `{ "feedType": "SYNTHETIC" }` to the backend
- [ ] Zustand `session.settings.feedType` is updated with the value returned in the HTTP 200 body
- [ ] Dropdown reverts to previous value on 400 or 500
- [ ] Redirect to `/login` on 401
- [ ] Tests cover: successful REAL selection, successful SYNTHETIC selection, 400 revert, 401 redirect, 500 revert
**Depends on:** none (API contract already exists in `user-openapi.yaml`)

---

### FE-2 — Ensure dropdown options include `REAL` with correct labels and tooltips

**Layer:** COMP
**Domain:** user
**Use case:** view-profile
**Implements:** `domain/usecases/view-profile.md` — Platform Settings tab, step 4; `domain/flows/update-user-settings.md` — step 2
**Inputs:**
- Existing feed-type dropdown component in `user/components/` (or `user/pages/`)
- `session.settings.feedType` from Zustand store
**Outputs:**
- Dropdown renders exactly two options:
  - Value `SYNTHETIC`, label "Synthetic Feed", tooltip "Market data generated by the platform, used for testing and playing around."
  - Value `REAL`, label "Real Market Feed", tooltip "Market data obtained from real trading platforms."
- Dropdown pre-populated with `session.settings.feedType` on render
- Loading/saving indicator shown while the PATCH is in-flight
- "Saved" confirmation shown briefly on success
- Inline error shown on the dropdown on 400/500; error text: "Invalid feed type selected." for 400, generic for 500
**Acceptance criteria:**
- [ ] Both `SYNTHETIC` and `REAL` options render with correct labels and tooltips
- [ ] Dropdown value reflects `session.settings.feedType` on mount
- [ ] Loading state is visible while PATCH is in-flight
- [ ] "Saved" indicator appears on success and disappears after a short delay
- [ ] Inline error shown and value reverted on 400 or 500
- [ ] Tests cover: renders both options, pre-population from store, success confirmation, error revert
**Depends on:** FE-1

---

## Dependency Summary

| Task ID | Title | Depends on |
|---|---|---|
| BE-1 | Add Finnhub OpenAPI spec to contracts folder | none |
| BE-2 | Configure OpenAPI generator for Finnhub client | BE-1 |
| BE-3 | Introduce `MarketDataFeedAdapter` interface | none |
| BE-4 | Implement `FinnhubPriceFeedAdapter` | BE-2, BE-3 |
| BE-5 | Wire feed-type routing to deliver real Finnhub data | BE-4 |
| BE-6 | Add `finnhub.api-key` to `application.yml` | BE-4 |
| FE-1 | Wire feed-type toggle to send REAL/SYNTHETIC values | none |
| FE-2 | Ensure dropdown options include REAL with labels and tooltips | FE-1 |
