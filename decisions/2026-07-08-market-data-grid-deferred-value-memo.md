# Decision: useDeferredValue + React.memo for MarketDataGrid to prevent live feed blocking navigation

**Date:** 2026-07-08
**Status:** accepted
**Fixes:** GitHub issue #61 — "Cannot switch back from trade view to profile/accounts"

## Context

The WebSocket price feed dispatches a TICK message every 250 ms (per the
`2026-06-25-websocket-market-data-feed` decision). Each tick caused
`useMarketDataFeed` to call `setRows`, which triggered a synchronous React
state update → a full re-render of `StockTradingPage` and all its children.

At 4 renders/second the browser's event queue was saturated with committed
React work. User-initiated clicks on `<NavLink>` elements in the Sidebar were
either not dispatched or dispatched but immediately preempted by the next
rendering cycle before React Router could process the navigation transition.
The result: the user could not leave the `/trade` route while the live feed
was active.

## Decision

Two changes applied together:

1. **`useDeferredValue` on `rows` in `StockTradingPage`** — React 19 treats
   deferred values as low-priority work. When a higher-priority event (e.g. a
   navigation click) arrives, React processes it first and interrupts any
   in-progress deferred render. This restores navigability without removing or
   throttling the feed.

2. **`React.memo` on `MarketDataGrid`** — prevents the grid from re-rendering
   when its `onBuy` callback reference changes due to a parent re-render
   triggered by a different piece of state. Combined with the deferred rows,
   `MarketDataGrid` only re-renders when the deferred rows value is committed
   by React, which happens between frames rather than synchronously.

## Alternatives considered

- **Throttling `setRows` in `useMarketDataFeed`** — would reduce update
  frequency but introduces a timer that is hard to test and leaks if the
  component unmounts mid-interval. Rejected.

- **Moving the WebSocket connection outside of React state** — would require
  a ref-based architecture that deviates significantly from TanStack Query
  conventions used elsewhere. Rejected.

- **`useTransition` wrapping the rows state update** — `useTransition` is
  appropriate for actions in event handlers, not for external push-based data.
  `useDeferredValue` is the correct primitive for derived data that updates
  frequently from an external source.

## Consequences

- `StockTradingPage` imports `useDeferredValue` from React and passes
  `deferredRows` to `MarketDataGrid` instead of `rows` directly.
- `MarketDataGrid` is exported as `export const MarketDataGrid = memo(...)`.
  The named export contract is unchanged — all existing imports continue to
  work without modification.
- The grid may display data that is one tick behind during high-priority
  interactions. This is acceptable for a paper trading simulator where exact
  real-time precision is not a hard requirement.
