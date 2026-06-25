# Tasks: Trade Stock Page — Real-Time Market Data Feed

**Use case:** `domain/usecases/trade-stock-page`
**Flows:** `domain/flows/market-data-websocket-feed` (Flows A–E), `domain/flows/manage-asset-subscriptions` (Flows A–D)
**Models:** `domain/model/market-data-snapshot`, `domain/model/asset-subscription`, `domain/model/session`

---

## Files read during decomposition

| File | Status |
|---|---|
| `AGENTS.md` | read |
| `domain/model/account.md` | read |
| `domain/model/asset-subscription.md` | read |
| `domain/model/ledger-entry.md` | read |
| `domain/model/market-data-snapshot.md` | read |
| `domain/model/session.md` | read |
| `domain/model/user.md` | read |
| `domain/flows/manage-asset-subscriptions.md` | read |
| `domain/flows/market-data-websocket-feed.md` | read |
| `domain/flows/user-login.md` | read |
| `domain/flows/user-session.md` | read |
| `domain/flows/open-account.md` | read |
| `domain/flows/account-top-up.md` | read |
| `domain/usecases/trade-stock-page.md` | read |
| `standards/architecture.md` | read |
| `standards/backend.md` | read |
| `standards/frontend.md` | read |
| Existing BE source files (controller, service, model, repo, events, config) | read |
| Existing FE source files (api, hooks, types, components, page) | read |

---

## Notes

- **No DB/REPO tasks** — `MarketDataSnapshot` is in-memory only; no new JPA entities or repositories.
- **`build.gradle.kts` WebSocket dependency** — `spring-boot-starter-websocket` must be added. This is a prerequisite of SVC-1 and is called out in that task's acceptance criteria.
- **`PriceFeedGenerator` abstraction** — the interface and random implementation are in the same layer (SVC). The interface is the public boundary; the implementation is wired via Spring; the feed dispatcher (SVC-2) depends only on the interface.
- **EVT-1** — the event listener methods live on `MarketDataFeedService` (SVC layer class) but are listed as an EVT task because they are `@EventListener` handlers and represent the async messaging integration point. The implementer may implement them as part of SVC-2 but must verify the EVT-1 acceptance criteria independently.
- **Frontend WebSocket client** — placed in `domains/marketdata/api/` following the existing domain-api pattern. Does not use Axios (not HTTP).
- **Grid library** — no specific library mandated. Acceptance criteria specify behaviour (sortable columns, scrollable container); the implementer chooses the implementation approach.

---

## EXCEPTION layer

### EXCEPTION-1 — Add WebSocket-specific exception classes

**Layer:** Exception
**Domain:** marketdata
**Use case:** trade-stock-page
**Implements:** `market-data-websocket-feed` Flow A — Error Cases (Missing userId, Unknown userId, Backend error during snapshot)
**Inputs:** none (new classes)
**Outputs:**
- `org.dpp.tradelab.marketdata.exception.WebSocketAuthException` — close code `4401`
- `org.dpp.tradelab.marketdata.exception.WebSocketFeedException` — close code `4500`

**Acceptance criteria:**
- [ ] `WebSocketAuthException` is a plain Kotlin `class` in `org.dpp.tradelab.marketdata.exception`, extending `RuntimeException`, with a single `message: String` constructor parameter
- [ ] `WebSocketFeedException` is a plain Kotlin `class` in `org.dpp.tradelab.marketdata.exception`, extending `RuntimeException`, with a single `message: String` constructor parameter
- [ ] Neither class has any Spring annotations
- [ ] Unit tests exist for both classes verifying the message is propagated correctly

**Depends on:** none

---

## SVC layer

### SVC-1 — Implement `PriceFeedGenerator` interface, `RandomPriceFeedGenerator`, and `MarketDataSnapshot` data class

**Layer:** Service
**Domain:** marketdata
**Use case:** trade-stock-page
**Implements:** `market-data-websocket-feed` Flow B — Step 1 (generate tick); `domain/model/market-data-snapshot` Business Rules (random generation, 1–10 tickers per tick, 250 ms interval, 3 d.p. price fields, PriceFeedGenerator abstraction)
**Inputs:**
- `supportedTickerConfig: SupportedTickerConfig` — injected into `RandomPriceFeedGenerator`

**Outputs:**
- `org.dpp.tradelab.marketdata.model.MarketDataSnapshot` — Kotlin `data class` (NOT `@Entity`) with fields: `ticker: String`, `companyName: String`, `currentPrice: BigDecimal`, `open: BigDecimal`, `dayLow: BigDecimal`, `fiftyTwoWeekHigh: BigDecimal`, `updatedAt: Instant`
- `org.dpp.tradelab.marketdata.service.PriceFeedGenerator` — Kotlin interface in `marketdata.service` with exactly one method: `generateTick(): List<MarketDataSnapshot>`
- `org.dpp.tradelab.marketdata.service.RandomPriceFeedGenerator` — Spring `@Component` implementing `PriceFeedGenerator`. Selects between 1 and 10 tickers at random (no duplicates per call) from `SupportedTickerConfig.getAll()`. Generates random positive `BigDecimal` values with scale 3 for all four price fields. Sets `updatedAt` to `Instant.now()`.
- `build.gradle.kts` updated with `implementation("org.springframework.boot:spring-boot-starter-websocket")` in the `dependencies` block

**Acceptance criteria:**
- [ ] `MarketDataSnapshot` is a `data class` in `org.dpp.tradelab.marketdata.model` — no `@Entity`, no `@Table`, no JPA annotations
- [ ] `PriceFeedGenerator` interface is in `org.dpp.tradelab.marketdata.service` with exactly one method returning `List<MarketDataSnapshot>`
- [ ] `RandomPriceFeedGenerator` implements `PriceFeedGenerator`, is annotated `@Component`, and depends on `SupportedTickerConfig` via constructor injection
- [ ] Each call to `generateTick()` returns between 1 and 10 items (inclusive)
- [ ] No ticker appears more than once in the result of a single `generateTick()` call
- [ ] All four price fields (`currentPrice`, `open`, `dayLow`, `fiftyTwoWeekHigh`) are positive `BigDecimal` with scale 3 (i.e. `.setScale(3, RoundingMode.HALF_UP)`)
- [ ] `build.gradle.kts` declares `spring-boot-starter-websocket` in the `dependencies` block
- [ ] Unit tests for `RandomPriceFeedGenerator`: result size is between 1 and 10; all price fields are positive; `updatedAt` is non-null; no duplicate tickers in a single result

**Depends on:** none

---

### SVC-2 — Implement `MarketDataFeedService` — in-memory snapshot cache, subscription lookup, session registry, scheduled dispatch

**Layer:** Service
**Domain:** marketdata
**Use case:** trade-stock-page
**Implements:** `market-data-websocket-feed` Flows A (Steps 3–4), B (Steps 2–4), C (Steps 1–3), D (Steps 2–3), E (Step 2); `domain/model/market-data-snapshot` Behaviors (Seed, Update, Snapshot); `domain/model/asset-subscription` Business Rules (startup seeding of in-memory lookup)
**Inputs:**
- `assetSubscriptionRepository: AssetSubscriptionRepository`
- `priceFeedGenerator: PriceFeedGenerator`
- `supportedTickerConfig: SupportedTickerConfig`
- `AssetSubscribedEvent` via Spring application event bus
- `AssetUnsubscribedEvent` via Spring application event bus

**Outputs:**
- `org.dpp.tradelab.marketdata.service.MarketDataFeedService` — Spring `@Service`

  **Internal state (all thread-safe):**
  - `snapshotCache: ConcurrentHashMap<String, MarketDataSnapshot>` — latest known snapshot per ticker
  - `tickerToUsers: ConcurrentHashMap<String, MutableSet<UUID>>` — ticker → set of subscribed userIds
  - `userToTickers: ConcurrentHashMap<UUID, MutableSet<String>>` — userId → set of subscribed tickers
  - `activeSessions: ConcurrentHashMap<UUID, WebSocketSession>` — userId → open WebSocket session

  **`@PostConstruct init()`:**
  - Load all `AssetSubscription` rows and populate `tickerToUsers` and `userToTickers`
  - Seed `snapshotCache` with one entry per supported ticker using `priceFeedGenerator.generateTick()` (call it in a loop or call it once and supplement with direct construction for uncovered tickers — all supported tickers must have an entry)

  **`@Scheduled(fixedDelay = 250)` `dispatchTicks()`:**
  - Call `priceFeedGenerator.generateTick()` → for each returned snapshot: overwrite `snapshotCache`; look up `tickerToUsers[snapshot.ticker]`; for each subscribed userId that has an active session in `activeSessions`, call `sendTick(session, snapshot)`

  **`sendTick(session: WebSocketSession, snapshot: MarketDataSnapshot)`:**
  - Serialises the snapshot as `{ "type": "TICK", "data": { … } }` JSON and calls `session.sendMessage(TextMessage(json))`

  **`getSnapshotForUser(userId: UUID): List<MarketDataSnapshot>`:**
  - Returns `snapshotCache` entries for every ticker in `userToTickers[userId]`; skips tickers with no cache entry

  **`registerSession(userId: UUID, session: WebSocketSession)`:**
  - Adds the session to `activeSessions`

  **`removeSession(userId: UUID)`:**
  - Removes the session from `activeSessions`

  **`@EventListener onAssetSubscribed(event: AssetSubscribedEvent)`:**
  - For each ticker in `event.tickers`: add `event.userId` to `tickerToUsers[ticker]`; add ticker to `userToTickers[event.userId]`
  - If the user has an active session, immediately call `sendTick` for each new ticker using the current `snapshotCache` entry

  **`@EventListener onAssetUnsubscribed(event: AssetUnsubscribedEvent)`:**
  - For each ticker in `event.tickers`: remove `event.userId` from `tickerToUsers[ticker]`; remove ticker from `userToTickers[event.userId]`

- `@EnableScheduling` added to a `@Configuration` class (or to `TradingLabApplication`)

**Acceptance criteria:**
- [ ] After `@PostConstruct`, `snapshotCache` contains an entry for every ticker in `SupportedTickerConfig.getAll()`
- [ ] After `@PostConstruct`, `tickerToUsers` and `userToTickers` are populated from the database
- [ ] `dispatchTicks()` is annotated `@Scheduled(fixedDelay = 250)` and only dispatches to userIds present in `activeSessions`
- [ ] `sendTick` JSON payload has `"type": "TICK"` and `"data"` containing the snapshot fields with prices serialised to 3 decimal places
- [ ] `registerSession` and `removeSession` are thread-safe
- [ ] `getSnapshotForUser` returns only tickers the userId is subscribed to
- [ ] `onAssetSubscribed` sends an immediate `TICK` for each new ticker to the user's active session (if connected)
- [ ] `onAssetUnsubscribed` removes the tickers from both maps; no further ticks dispatched
- [ ] If the user has no active session when either event fires, no exception is thrown
- [ ] `@EnableScheduling` is present
- [ ] Unit tests: `@PostConstruct` seeding; `dispatchTicks` dispatches only to subscribed connected users; `onAssetSubscribed` updates maps and sends immediate tick when connected; `onAssetSubscribed` updates maps silently when disconnected; `onAssetUnsubscribed` updates maps; `removeSession` stops dispatch

**Depends on:** SVC-1, EXCEPTION-1

---

### SVC-3 — Implement `MarketDataWebSocketHandler` — handshake, snapshot push, disconnect

**Layer:** Service
**Domain:** marketdata
**Use case:** trade-stock-page
**Implements:** `market-data-websocket-feed` Flow A (Steps 1–5), Flow E (Steps 1–2); Error Cases (Missing userId → close 4401, Unknown userId → close 4401, Backend error → close 4500)
**Inputs:**
- `marketDataFeedService: MarketDataFeedService`
- `WebSocketSession` with URI query parameter `userId`

**Outputs:**
- `org.dpp.tradelab.marketdata.service.MarketDataWebSocketHandler` — Spring `TextWebSocketHandler` subclass

  **`afterConnectionEstablished(session: WebSocketSession)`:**
  - Extract `userId` from `session.uri.query` (`?userId=…`)
  - If absent or blank: `session.close(CloseStatus(4401, "userId required"))`; return
  - Parse as `UUID`; if invalid format: `session.close(CloseStatus(4401, "invalid userId"))`; return
  - Call `marketDataFeedService.registerSession(userId, session)`
  - Call `marketDataFeedService.getSnapshotForUser(userId)` → build JSON: `{ "type": "SNAPSHOT", "data": [ { "ticker": "…", "companyName": "…", "currentPrice": 0.000, "open": 0.000, "dayLow": 0.000, "fiftyTwoWeekHigh": 0.000 } ] }` → `session.sendMessage(TextMessage(json))`
  - On any uncaught exception: `session.close(CloseStatus(4500, "internal error"))`

  **`afterConnectionClosed(session: WebSocketSession, status: CloseStatus)`:**
  - Extract `userId` from `session.uri.query`; if parseable as `UUID`, call `marketDataFeedService.removeSession(userId)`

**Acceptance criteria:**
- [ ] Connection without `userId` query param is rejected with `CloseStatus(4401, …)`
- [ ] Connection with malformed UUID is rejected with `CloseStatus(4401, …)`
- [ ] Valid connection immediately receives a `SNAPSHOT` JSON message with `type: "SNAPSHOT"` and `data` array
- [ ] `SNAPSHOT` data contains only the tickers the user is subscribed to
- [ ] Prices in the `SNAPSHOT` JSON are serialised to exactly 3 decimal places
- [ ] Disconnect calls `marketDataFeedService.removeSession(userId)`
- [ ] Internal error during snapshot causes `CloseStatus(4500, …)`
- [ ] Unit tests: reject missing userId; reject invalid UUID; snapshot sent on connect with correct shape; disconnect removes session; exception → close 4500

**Depends on:** SVC-2

---

## EVT layer

### EVT-1 — Verify event listener wiring for subscription changes in `MarketDataFeedService`

**Layer:** Event
**Domain:** marketdata
**Use case:** trade-stock-page
**Implements:** `market-data-websocket-feed` Flow C (Steps 1–3), Flow D (Steps 2–3)
**Inputs:**
- `AssetSubscribedEvent` emitted by `AssetSubscriptionService.bulkAdd`
- `AssetUnsubscribedEvent` emitted by `AssetSubscriptionService.bulkRemove`

**Outputs:**
- `@EventListener fun onAssetSubscribed(event: AssetSubscribedEvent)` on `MarketDataFeedService`
- `@EventListener fun onAssetUnsubscribed(event: AssetUnsubscribedEvent)` on `MarketDataFeedService`

> Note: These methods are physically implemented in SVC-2. This EVT task is a dedicated checklist to ensure the event wiring is correct and fully tested in isolation.

**Acceptance criteria:**
- [ ] `onAssetSubscribed` is annotated `@EventListener` and its parameter type is `AssetSubscribedEvent` (from `org.dpp.tradelab.marketdata.messaging`)
- [ ] `onAssetUnsubscribed` is annotated `@EventListener` and its parameter type is `AssetUnsubscribedEvent`
- [ ] After `onAssetSubscribed`, the next call to `dispatchTicks` that touches a newly subscribed ticker sends it to the subscribing user
- [ ] After `onAssetSubscribed`, if the user's session is active, an immediate `TICK` is sent for each new ticker's current snapshot
- [ ] After `onAssetUnsubscribed`, no further `TICK` messages are sent to the user for the removed ticker(s)
- [ ] If the user has no active session when either event fires, the lookup maps are still updated and no exception is thrown
- [ ] Integration test (Spring context): `AssetSubscriptionService.bulkAdd` → `AssetSubscribedEvent` → `MarketDataFeedService.onAssetSubscribed` → lookup updated
- [ ] Integration test: `AssetSubscriptionService.bulkRemove` → `AssetUnsubscribedEvent` → `MarketDataFeedService.onAssetUnsubscribed` → lookup updated

**Depends on:** SVC-2

---

## CONTROLLER layer

### CONTROLLER-1 — Implement `MarketDataWebSocketConfig` — register WebSocket endpoint

**Layer:** Controller
**Domain:** marketdata
**Use case:** trade-stock-page
**Implements:** `market-data-websocket-feed` Flow A — Step 1 (endpoint path `/api/v1/market-data/feed`)
**Inputs:**
- `marketDataWebSocketHandler: MarketDataWebSocketHandler`

**Outputs:**
- `org.dpp.tradelab.marketdata.controller.MarketDataWebSocketConfig` — `@Configuration` class implementing `WebSocketConfigurer`

  **`registerWebSocketHandlers(registry: WebSocketHandlerRegistry)`:**
  - `registry.addHandler(marketDataWebSocketHandler, "/api/v1/market-data/feed").setAllowedOrigins("*")`

**Acceptance criteria:**
- [ ] `@Configuration` class implementing `WebSocketConfigurer` exists in `org.dpp.tradelab.marketdata.controller`
- [ ] `MarketDataWebSocketHandler` is registered at path `/api/v1/market-data/feed`
- [ ] `setAllowedOrigins("*")` is set
- [ ] Spring Boot test: application context loads without error with the new config present
- [ ] When the application is running, a WebSocket upgrade request to `ws://localhost:8080/api/v1/market-data/feed?userId={uuid}` succeeds (HTTP 101)

**Depends on:** SVC-3

---

## API-CONTRACT layer

### API-CONTRACT-1 — Document WebSocket feed endpoint and message schemas in `marketdata-openapi.yaml`

**Layer:** API Contract
**Domain:** marketdata
**Use case:** trade-stock-page
**Implements:** `market-data-websocket-feed` Flow A (Step 1), WebSocket Message Types table
**Inputs:**
- Existing `services/contract/marketdata-openapi.yaml`
- New path: `GET /market-data/feed` (WebSocket upgrade, `operationId: connectMarketDataFeed`)
- Query parameter: `userId` (required, string, format uuid)
- New schemas: `MarketDataUpdate`, `SnapshotMessage`, `TickMessage`
- Close codes: `4401` (auth failure), `4500` (server error)

**Outputs:**
- Updated `services/contract/marketdata-openapi.yaml` with the following additions:

  **New path `/market-data/feed`:**
  ```yaml
  /market-data/feed:
    get:
      tags:
        - Market Data
      summary: Connect to real-time market data feed (WebSocket)
      operationId: connectMarketDataFeed
      description: >
        WebSocket upgrade endpoint. After a successful HTTP 101 handshake, the server
        immediately pushes a SNAPSHOT message containing current price data for all
        of the user's subscribed tickers. Subsequent TICK messages are pushed as the
        price feed updates (~every 250ms).
        Close codes: 4401 — userId missing or invalid; 4500 — internal server error.
      parameters:
        - name: userId
          in: query
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '101':
          description: WebSocket handshake successful
        '4401':
          description: userId missing or invalid
        '4500':
          description: Internal server error during snapshot
  ```

  **New schemas in `components/schemas`:**
  - `MarketDataUpdate`: `ticker` (string), `companyName` (string), `currentPrice` (number, multipleOf 0.001), `open` (number, multipleOf 0.001), `dayLow` (number, multipleOf 0.001), `fiftyTwoWeekHigh` (number, multipleOf 0.001)
  - `SnapshotMessage`: `type` (string, enum: `[SNAPSHOT]`), `data` (array of `$ref: MarketDataUpdate`)
  - `TickMessage`: `type` (string, enum: `[TICK]`), `data` (`$ref: MarketDataUpdate`)

**Acceptance criteria:**
- [ ] Path `/market-data/feed` exists with `get` operation and `operationId: connectMarketDataFeed`
- [ ] `userId` query parameter is present, required, type string, format uuid
- [ ] `MarketDataUpdate` schema is defined in `components/schemas` with all six fields: `ticker` (string), `companyName` (string), `currentPrice` / `open` / `dayLow` / `fiftyTwoWeekHigh` (all number)
- [ ] `SnapshotMessage` schema defined with `type` enum `[SNAPSHOT]` and `data` as array of `MarketDataUpdate`
- [ ] `TickMessage` schema defined with `type` enum `[TICK]` and `data` as single `MarketDataUpdate`
- [ ] All pre-existing paths and schemas are unchanged
- [ ] YAML is valid OpenAPI 3.0.3 (passes `openapi-generator validate`)

**Depends on:** CONTROLLER-1

---

## CLI layer (Frontend)

### CLI-1 — Implement `marketDataFeedApi.ts` — browser WebSocket client

**Layer:** API Client
**Domain:** marketdata
**Use case:** trade-stock-page
**Implements:** `market-data-websocket-feed` Flow A (Steps 1, 4–5), Flow B (Step 5), Flow E (Step 1); use case failure scenario (single reconnect on unexpected close)
**Inputs:**
- `userId: string` — passed as query param on WebSocket URL
- Inbound `SnapshotMessage`: `{ type: 'SNAPSHOT', data: MarketDataUpdate[] }`
- Inbound `TickMessage`: `{ type: 'TICK', data: MarketDataUpdate }`

**Outputs:**
- `services/front-end/src/domains/marketdata/api/marketDataFeedApi.ts` exporting:
  - `MarketDataUpdate` interface: `ticker: string`, `companyName: string`, `currentPrice: number`, `open: number`, `dayLow: number`, `fiftyTwoWeekHigh: number`
  - `SnapshotMessage` interface: `type: 'SNAPSHOT'`, `data: MarketDataUpdate[]`
  - `TickMessage` interface: `type: 'TICK'`, `data: MarketDataUpdate`
  - `FeedMessage` discriminated union: `SnapshotMessage | TickMessage`
  - `connectMarketDataFeed(userId: string, onMessage: (msg: FeedMessage) => void, onError: (code: number) => void, onClose: () => void): () => void`
    - Opens a native browser `WebSocket` to `ws://${window.location.host}/api/v1/market-data/feed?userId=${userId}`
    - On `message`: parses JSON, narrows to `FeedMessage`, calls `onMessage`
    - On clean close (code 1000): calls `onClose`
    - On unexpected close (code ≠ 1000): attempts exactly one reconnect; if reconnect also closes unexpectedly, calls `onError(code)` — no further reconnects
    - Returns a cleanup function that calls `socket.close(1000)` on the active socket

**Acceptance criteria:**
- [ ] `MarketDataUpdate`, `SnapshotMessage`, `TickMessage`, `FeedMessage` are all exported
- [ ] No `any` types anywhere in the file
- [ ] WebSocket URL is `ws://${window.location.host}/api/v1/market-data/feed?userId=${userId}`
- [ ] `onMessage` is called for both `SNAPSHOT` and `TICK` message types
- [ ] Exactly one reconnect is attempted on unexpected close; `onError` is called if the reconnect also fails
- [ ] Cleanup function closes the socket with code `1000`
- [ ] Unit tests (mock native `WebSocket`): correct URL; `onMessage` called on `SNAPSHOT`; `onMessage` called on `TICK`; single reconnect on unexpected close; `onError` after reconnect failure; cleanup closes socket

**Depends on:** API-CONTRACT-1

---

## STATE layer (Frontend)

### STATE-1 — Implement `useMarketDataFeed` hook — grid rows state and WebSocket lifecycle

**Layer:** State
**Domain:** marketdata
**Use case:** trade-stock-page
**Implements:** `market-data-websocket-feed` Flow A (Steps 1, 5), Flow B (Steps 4–5), Flow C (Step 4), Flow D (Step 1), Flow E (Step 1); use case failure scenarios (connecting state, error banner, reconnect, lost banner)
**Inputs:**
- `userId: string`
- `subscribedTickers: string[]` — current list of subscribed ticker symbols (used to drive row removal)
- `connectMarketDataFeed` from `marketDataFeedApi.ts`

**Outputs:**
- `services/front-end/src/domains/marketdata/hooks/useMarketDataFeed.ts` exporting:

  ```typescript
  function useMarketDataFeed(
    userId: string,
    subscribedTickers: string[]
  ): {
    rows: MarketDataUpdate[]
    feedStatus: 'connecting' | 'connected' | 'error' | 'lost'
  }
  ```

  **Behaviour:**
  - On mount: set `feedStatus = 'connecting'`; call `connectMarketDataFeed(userId, onMessage, onError, onClose)`; store cleanup in a `useRef`
  - On `SNAPSHOT` message: replace `rows` with the full array; set `feedStatus = 'connected'`
  - On `TICK` message: find the row with matching `ticker` and update it in place (immutable update); if no row exists (new subscription mid-session add), append the row
  - When `subscribedTickers` changes: remove any row whose `ticker` is not in `subscribedTickers`
  - On `onError`: set `feedStatus = 'lost'`
  - On `onClose` (unexpected clean close that is not an error): set `feedStatus = 'lost'`
  - On unmount: call the cleanup function

**Acceptance criteria:**
- [ ] Hook is in `services/front-end/src/domains/marketdata/hooks/useMarketDataFeed.ts`
- [ ] `rows` is `[]` before `SNAPSHOT` is received
- [ ] `feedStatus` is `'connecting'` before `SNAPSHOT` is received
- [ ] `SNAPSHOT` replaces `rows` entirely and sets `feedStatus = 'connected'`
- [ ] `TICK` updates the matching row in place (same object identity is not required, but the rest of the rows array must not change)
- [ ] `TICK` for a previously unseen ticker appends a new row (mid-session subscription add)
- [ ] When a ticker is removed from `subscribedTickers`, its row is removed from `rows`
- [ ] `feedStatus` becomes `'lost'` on `onError` or unexpected `onClose`
- [ ] Cleanup function is called on hook unmount
- [ ] No server state stored in Zustand — hook manages local state via `useState` / `useRef`
- [ ] No `any` types
- [ ] Unit tests using `renderHook`: `SNAPSHOT` populates rows and sets status to connected; `TICK` updates correct row without affecting others; `TICK` for new ticker appends row; ticker removal removes row; `feedStatus` transitions correctly; cleanup called on unmount

**Depends on:** CLI-1

---

## COMP layer (Frontend)

### COMP-1 — Implement `MarketDataGrid` component — sortable, scrollable price table

**Layer:** Component
**Domain:** stocktrading
**Use case:** trade-stock-page
**Implements:** `domain/usecases/trade-stock-page` Grid Specification (all six columns, ascending/descending/unsorted sort per column, horizontal and vertical scrolling, empty state, connecting/error/lost states, in-place row update keyed by ticker)
**Inputs:**
- `rows: MarketDataUpdate[]`
- `feedStatus: 'connecting' | 'connected' | 'error' | 'lost'`

**Outputs:**
- `services/front-end/src/domains/stocktrading/components/MarketDataGrid.tsx`
- `services/front-end/src/domains/stocktrading/components/MarketDataGrid.test.tsx`

**Component behaviour:**
- Columns: `Ticker`, `Company Name`, `Current Price (USD)`, `Open (USD)`, `Day Low (USD)`, `52W High (USD)`
- Price columns display values formatted to exactly 3 decimal places (e.g. `123.450`)
- Each column header is a clickable sort toggle cycling: unsorted → ascending → descending → unsorted. Only one column sorted at a time.
- Table container has `overflow-x: auto` and `overflow-y: auto` (horizontal + vertical scroll)
- `feedStatus === 'connecting'`: render a "Connecting…" loading indicator; no table
- `feedStatus === 'error'`: render error banner "Unable to connect to price feed."
- `feedStatus === 'lost'`: render error banner "Connection lost. Please refresh the page."
- `feedStatus === 'connected'` and `rows.length === 0`: render empty-state message "No price data yet."
- `feedStatus === 'connected'` and `rows.length > 0`: render table
- Each table row has `key={row.ticker}`

**Acceptance criteria:**
- [ ] `MarketDataGridProps` interface is defined with `rows: MarketDataUpdate[]` and `feedStatus: 'connecting' | 'connected' | 'error' | 'lost'`
- [ ] All six columns are rendered with the exact header labels above
- [ ] Price values are formatted to exactly 3 decimal places
- [ ] Clicking a column header sorts rows ascending; clicking again sorts descending; clicking a third time returns to original (unsorted) order
- [ ] Only one column is sorted at a time — sorting by a second column clears the first
- [ ] Table container allows both horizontal and vertical scrolling
- [ ] "Connecting…" state renders when `feedStatus === 'connecting'`
- [ ] Error banners render for `'error'` and `'lost'` states with the exact messages above
- [ ] Empty-state message renders when `feedStatus === 'connected'` and `rows` is empty
- [ ] Each row has `key={row.ticker}`
- [ ] No `any` types
- [ ] Unit tests: all columns render; sort ascending on first click, descending on second, unsorted on third; multi-column sort (second column click resets first); empty state; connecting state; error and lost banners; rows render with 3 d.p. prices

**Depends on:** STATE-1

---

## SCREEN layer (Frontend)

### SCREEN-1 — Update `StockTradingPage` to mount `MarketDataGrid` below the subscription list

**Layer:** Screen
**Domain:** stocktrading
**Use case:** trade-stock-page
**Implements:** `domain/usecases/trade-stock-page` Happy Path Steps 3–8; Failure Scenarios (WebSocket fails on mount, grid row removal on unsubscribe)
**Inputs:**
- Existing `StockTradingPage.tsx` (already renders subscription list, `AddTickerPanel`, `RemoveTickerBar`)
- `useMarketDataFeed` hook from `domains/marketdata/hooks/useMarketDataFeed`
- `MarketDataGrid` component from `domains/stocktrading/components/MarketDataGrid`
- `subscriptionsData` from the existing `useSubscriptions` hook (already present)

**Outputs:**
- Updated `services/front-end/src/domains/stocktrading/pages/StockTradingPage.tsx`
- Updated `services/front-end/src/domains/stocktrading/pages/StockTradingPage.test.tsx`

**Changes required:**
- Derive `subscribedTickers: string[]` from `subscriptionsData ?? []` (i.e. `subscriptionsData?.map(s => s.ticker) ?? []`)
- Call `const { rows, feedStatus } = useMarketDataFeed(user.userId, subscribedTickers)`
- Render `<MarketDataGrid rows={rows} feedStatus={feedStatus} />` below `<SubscriptionList … />` and below the existing remove/add controls

**Acceptance criteria:**
- [ ] `useMarketDataFeed` is called with `user.userId` and the current list of subscribed ticker symbols
- [ ] `MarketDataGrid` is rendered below the existing `<SubscriptionList … />` element
- [ ] `MarketDataGrid` receives `rows` and `feedStatus` directly from `useMarketDataFeed`
- [ ] No explicit row-removal code is added to the page — row removal is driven by the `subscribedTickers` change propagating into `useMarketDataFeed` after TanStack Query invalidates on successful `bulkRemove`
- [ ] All existing subscription management behaviour (`AddTickerPanel`, `RemoveTickerBar`, `SubscriptionList`) is unchanged
- [ ] No business logic is added to the page beyond hook call and prop passing
- [ ] Unit tests: `MarketDataGrid` is rendered; `useMarketDataFeed` is called with correct `userId` and `subscribedTickers`; tests mock `useMarketDataFeed` to control `rows` and `feedStatus`

**Depends on:** COMP-1, STATE-1

---

## Dependency summary

| Task ID | Title | Depends on |
|---|---|---|
| EXCEPTION-1 | WebSocket exception classes | none |
| SVC-1 | `PriceFeedGenerator` + `RandomPriceFeedGenerator` + `MarketDataSnapshot` data class | none |
| SVC-2 | `MarketDataFeedService` — cache, lookup, scheduling, session management, event listeners | SVC-1, EXCEPTION-1 |
| SVC-3 | `MarketDataWebSocketHandler` — handshake, snapshot, disconnect | SVC-2 |
| EVT-1 | Event listener wiring verification for subscription changes | SVC-2 |
| CONTROLLER-1 | `MarketDataWebSocketConfig` — register WebSocket endpoint | SVC-3 |
| API-CONTRACT-1 | Update `marketdata-openapi.yaml` with WebSocket schemas | CONTROLLER-1 |
| CLI-1 | `marketDataFeedApi.ts` — browser WebSocket client | API-CONTRACT-1 |
| STATE-1 | `useMarketDataFeed` hook | CLI-1 |
| COMP-1 | `MarketDataGrid` component | STATE-1 |
| SCREEN-1 | Update `StockTradingPage` | COMP-1, STATE-1 |
