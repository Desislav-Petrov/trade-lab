# Decision: Add `side` Field to the Order Entity

**Date:** 2026-07-14  
**Status:** accepted

## Context

The `Order` entity was originally modelled as a buy-only instrument. The `sell-stock` feature (issue #41) introduces the ability to sell stock from the Portfolio page. A sell order follows the same lifecycle as a buy order (`PENDING` → `FILLED` | `REJECTED`) and is processed by the same Stock Trading domain. Rather than introducing a separate entity, the cleanest extension is to add a `side` discriminator field to `Order`.

## Decision

Add a `side` enum field to `Order` with two values: `BUY` and `SELL`. All existing orders are `BUY`. Sell orders are `SELL`. The field is required on creation and immutable thereafter.

The `OrderFilledEvent` payload is extended to carry `side` so that downstream consumers (Portfolio, Ledger) can branch on it without inspecting the order record directly.

## Consequences

- `domain/model/order.md` is updated to add the `side` field, update the overview, and extend the business rules.
- `domain/flows/buy-stock.md` is unaffected in behaviour; the `side` value is implicitly `BUY` for all existing buy orders.
- `domain/flows/sell-stock.md` is the new flow that introduces `side: SELL`.
- The OpenAPI contract for `POST /api/v1/stock-orders` must be extended to include `side` in the request body.
- The `OrderFilledEvent` data class must be extended to carry `side`.
- The Portfolio domain's event listener must branch on `side` to call `handleOrderFilledBuy` or `handleOrderFilledSell`.
