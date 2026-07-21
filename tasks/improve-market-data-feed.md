# Tasks: Improve Market Data Feed Generation (Issue #111)

## Use Case Summary

Issue #111 targets the Market Data domain's `PriceFeedGenerator`. It requires:
1. Each ticker's initial price is seeded uniformly at random between **$200.000 and $400.000** (3 decimal places).
2. Each tick applies a **±0.5%–1.5%** change to the previous price, with direction chosen 50/50, magnitude drawn uniformly.
3. `MarketDataSnapshot` gains a `dayHigh` field (intra-session running maximum). `open` is frozen at seed time. `dayLow` tracks the running minimum. `dayHigh` and `fiftyTwoWeekHigh` both track the running maximum (semantically distinct but numerically identical in this simulation).

All domain model changes are recorded in `decisions/2026-07-21-market-data-snapshot-dayhigh.md`.

---

## SVC Layer

### SVC-1 — Add `dayHigh` to `MarketDataSnapshot` data class

**Layer:** SVC (non-JPA in-memory model — `marketdata.model`)
**Domain:** marketdata
**Use case:** improve-market-data-feed
**Implements:** `market-data-websocket-feed` Flow B step 2 — snapshot cache entry carries `dayHigh`
**Inputs:** existing `MarketDataSnapshot` at `org.dpp.tradelab.marketdata.model.MarketDataSnapshot`
**Outputs:** updated `MarketDataSnapshot` data class with `val dayHigh: BigDecimal` added after `dayLow` and before `fiftyTwoWeekHigh`
**Acceptance criteria:**
- [ ] `MarketDataSnapshot` has `val dayHigh: BigDecimal`
- [ ] All existing fields remain unchanged and in the same order
- [ ] `./gradlew build` compiles without errors
**Depends on:** none

---

### SVC-2 — Rewrite `RandomPriceFeedGenerator` with realistic seed and tick-change algorithm

**Layer:** SVC
**Domain:** marketdata
**Use case:** improve-market-data-feed
**Implements:** `market-data-websocket-feed` Flow B step 1 — new price generation rules from domain model `market-data-snapshot.md` (Seed and Update behaviours)
**Inputs:**
- `SupportedTickerConfig.getAll()` — map of ticker → companyName
- Internal per-ticker price state map (previous `currentPrice`, `open`, `dayLow`, `dayHigh`, `fiftyTwoWeekHigh`) held as instance state inside `RandomPriceFeedGenerator`

**Outputs:** Updated `RandomPriceFeedGenerator` where:
- On first call per ticker: `currentPrice` is drawn uniformly at random from $200.000–$400.000 (3dp). `open`, `dayLow`, `dayHigh`, and `fiftyTwoWeekHigh` are all set to this seed price.
- On every subsequent call per ticker: new price = `previousPrice × (1 ± magnitude)` where direction is 50/50 (increment or decrement) and magnitude is drawn uniformly from [0.005, 0.015]. Result rounded to 3dp. `dayLow` updated if new price < current `dayLow`. `dayHigh` and `fiftyTwoWeekHigh` updated if new price > current values. `open` never changes.
- `updatedAt` = `Instant.now()` on every tick.

**Acceptance criteria:**
- [ ] First emitted `currentPrice` per ticker is between 200.000 and 400.000 inclusive (3dp)
- [ ] `open` equals the first seed price and is unchanged across all subsequent ticks for that ticker
- [ ] Each subsequent `currentPrice` is the previous price modified by 0.5%–1.5% (either direction)
- [ ] `dayLow` is always ≤ the minimum of all previously emitted `currentPrice` values for that ticker
- [ ] `dayHigh` and `fiftyTwoWeekHigh` are always ≥ the maximum of all previously emitted `currentPrice` values for that ticker
- [ ] All price fields are `BigDecimal` at scale 3 (RoundingMode.HALF_UP)
- [ ] Unit test `RandomPriceFeedGeneratorTest` covers: seed range, tick direction (up and down paths), running min/max, `open` immutability
- [ ] `./gradlew test` passes

**Depends on:** SVC-1

---

### SVC-3 — Update `MarketDataFeedService.snapshotToJson` to serialise `dayHigh`

**Layer:** SVC
**Domain:** marketdata
**Use case:** improve-market-data-feed
**Implements:** `market-data-websocket-feed` Flow A step 4 and Flow B step 4 — outbound JSON must include `dayHigh`
**Inputs:**
- `MarketDataFeedService.snapshotToJson(snapshot: MarketDataSnapshot): String` at line 207
- Updated `MarketDataSnapshot` with `dayHigh` field (SVC-1)

**Outputs:** `snapshotToJson` emits JSON with `"dayHigh"` field at scale 3 between `"dayLow"` and `"fiftyTwoWeekHigh"`:
```
{"ticker":"...","companyName":"...","currentPrice":...,"open":...,"dayLow":...,"dayHigh":...,"fiftyTwoWeekHigh":...}
```

**Acceptance criteria:**
- [ ] `snapshotToJson` output contains `"dayHigh"` key with a 3dp decimal value
- [ ] Field order in JSON string: `ticker`, `companyName`, `currentPrice`, `open`, `dayLow`, `dayHigh`, `fiftyTwoWeekHigh`
- [ ] Existing fields are unaffected
- [ ] Unit test `MarketDataFeedServiceTest` has a test `snapshotToJson_withDayHigh_includesDayHighField` asserting the JSON string contains `"dayHigh"` with the correct value
- [ ] `./gradlew test` passes

**Depends on:** SVC-1

---

## API-CONTRACT Layer

### API-CONTRACT-1 — Add `dayHigh` to `MarketDataUpdate` schema in `marketdata-openapi.yaml`

**Layer:** API-CONTRACT
**Domain:** marketdata
**Use case:** improve-market-data-feed
**Implements:** `market-data-websocket-feed` Flow A step 5 and Flow B step 5 — WebSocket message contract
**Inputs:** `services/contract/marketdata-openapi.yaml`, `components/schemas/MarketDataUpdate`
**Outputs:** Updated `marketdata-openapi.yaml` where `MarketDataUpdate`:
- `required` array includes `dayHigh`
- `properties` block contains:
  ```yaml
  dayHigh:
    type: number
    multipleOf: 0.001
    example: 155.000
  ```
  positioned after `dayLow` and before `fiftyTwoWeekHigh`

**Acceptance criteria:**
- [ ] `MarketDataUpdate.required` includes `"dayHigh"`
- [ ] `MarketDataUpdate.properties.dayHigh` has `type: number`, `multipleOf: 0.001`, `example: 155.000`
- [ ] Property order in YAML: `ticker`, `companyName`, `currentPrice`, `open`, `dayLow`, `dayHigh`, `fiftyTwoWeekHigh`
- [ ] `./gradlew openApiGenerate` succeeds
- [ ] Generated `MarketDataUpdate.kt` in `build/generated/marketdata/` contains `val dayHigh: java.math.BigDecimal`

**Depends on:** SVC-1

---

## CLI Layer

### CLI-1 — Add `dayHigh` to frontend `MarketDataUpdate` TypeScript interface

**Layer:** CLI
**Domain:** marketdata
**Use case:** improve-market-data-feed
**Implements:** `market-data-websocket-feed` Flow A step 5 / Flow B step 5 — frontend receives and types `dayHigh`
**Inputs:** `services/front-end/src/domains/marketdata/api/marketDataFeedApi.ts`, `MarketDataUpdate` interface
**Outputs:** `MarketDataUpdate` interface contains `dayHigh: number` after `dayLow` and before `fiftyTwoWeekHigh`
**Acceptance criteria:**
- [ ] `MarketDataUpdate` interface has `dayHigh: number`
- [ ] `pnpm run build` succeeds with no TypeScript errors
- [ ] Existing `marketDataFeedApi.test.ts` still passes without modification
- [ ] `pnpm run test` passes

**Depends on:** API-CONTRACT-1

---

## COMP Layer

### COMP-1 — Add `dayHigh` column to `MarketDataGrid`

**Layer:** COMP
**Domain:** marketdata / stocktrading
**Use case:** improve-market-data-feed
**Implements:** `trade-stock-page` use case Grid Specification (columns list); `market-data-websocket-feed` Flow A step 5
**Inputs:**
- `services/front-end/src/domains/stocktrading/components/MarketDataGrid.tsx`
- `COLUMNS` constant (currently 6 entries)
- Updated `MarketDataUpdate` type with `dayHigh` (CLI-1)

**Outputs:** `MarketDataGrid` renders a new `Day High (USD)` column between `Day Low (USD)` and `52W High (USD)`:
- `COLUMNS` entry: `{ key: 'dayHigh', label: 'Day High (USD)' }`
- Row `<td>` renders `{row.dayHigh.toFixed(3)}`
- Column is sortable (ascending → descending → unsorted) — same behaviour as all numeric columns

**Acceptance criteria:**
- [ ] `COLUMNS` has 7 entries; `dayHigh` is at index 5 (between `dayLow` and `fiftyTwoWeekHigh`)
- [ ] Column header displays `Day High (USD)`
- [ ] Each row displays `dayHigh` value formatted to 3dp
- [ ] Clicking `Day High (USD)` header cycles sort ascending → descending → unsorted
- [ ] `MarketDataGrid.test.tsx` covers: `dayHigh` column header is rendered, `dayHigh` value is displayed, sorting by `dayHigh` asc/desc works
- [ ] `pnpm run test` passes

**Depends on:** CLI-1

---

## Dependency Summary

| Task ID | Title | Depends on |
|---------|-------|------------|
| SVC-1 | Add `dayHigh` to `MarketDataSnapshot` data class | none |
| SVC-2 | Rewrite `RandomPriceFeedGenerator` (seed $200–$400, ±0.5–1.5% ticks) | SVC-1 |
| SVC-3 | Update `snapshotToJson` to serialise `dayHigh` | SVC-1 |
| API-CONTRACT-1 | Add `dayHigh` to OpenAPI `MarketDataUpdate` schema | SVC-1 |
| CLI-1 | Add `dayHigh` to frontend `MarketDataUpdate` TypeScript interface | API-CONTRACT-1 |
| COMP-1 | Add `dayHigh` column to `MarketDataGrid` | CLI-1 |
