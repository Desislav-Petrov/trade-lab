# Decision: Offload WebSocket tick dispatch to a bounded worker pool

**Date:** 2026-09-10
**Status:** accepted

## Context

The market-data feed publishes a `MarketDataTickEvent` roughly every 250 ms.
`MarketDataFeedService.handleMarketDataTick` — invoked synchronously by
`MarketDataEventListener` on the single scheduler thread that drives the feed —
iterated every subscribed user and called `WebSocketSession.sendMessage(...)`
inline.

`sendMessage` is blocking network I/O. Performing it inline on the tick thread
means:

1. A single slow or back-pressured client stalls delivery to **all** other
   subscribers for that tick.
2. The blocking send delays the next scheduled tick, degrading the feed for
   everyone.
3. `WebSocketSession` is not safe for concurrent sends, so any future
   parallelism would corrupt frames.

Backend standards (`standards/backend.md`) state that introducing asynchronous
processing requires a decision-log entry — hence this record.

## Decision

Two changes applied together in `MarketDataFeedService`:

1. **Bounded dispatch pool.** A dedicated `ThreadPoolTaskExecutor`
   (`marketDataTickDispatchExecutor`, defined in `MarketDataDispatchConfig`)
   fans out the per-user `sendMessage` calls. The tick thread only enqueues
   work and returns immediately. The pool is bounded (core 2 / max 4 / queue
   1000) with a `DiscardOldestPolicy`: under sustained backpressure the oldest
   queued tick is dropped rather than blocking the tick thread, because a stale
   price tick has no value in a live feed.

2. **Per-session serialisation.** `registerSession` now wraps each
   `WebSocketSession` in a `ConcurrentWebSocketSessionDecorator`
   (10 s send-time limit, 512 KB buffer). This makes concurrent sends from the
   pool safe per session and force-closes a client that cannot keep up instead
   of leaking a blocked worker thread.

## Alternatives considered

- **`@Async` on the handler method** — coarser (offloads the whole fan-out
  loop as one task) and couples the async concern to the service method, which
  the listener-class pattern deliberately avoids. Rejected in favour of an
  explicitly injected `Executor`.
- **Reactive WebSocket stack (WebFlux)** — a large architectural change for a
  single hot path. Out of scope. Rejected.
- **Throttling ticks** — addresses render pressure (already handled on the
  frontend via `useDeferredValue`) but not the head-of-line blocking on the
  server. Rejected.

## Consequences

- `MarketDataFeedService` gains a constructor dependency on an `Executor`
  (`@Qualifier("marketDataTickDispatchExecutor")`). Unit tests inject a
  synchronous executor (`{ it.run() }`) so assertions remain deterministic.
- Ticks are now delivered asynchronously; ordering per session is preserved by
  the decorator, but a tick may be dropped under extreme backpressure.
- The connect-time `sendSnapshot` remains synchronous on the connecting user's
  own thread and is unchanged.
