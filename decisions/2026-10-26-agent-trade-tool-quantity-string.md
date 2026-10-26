# Decision: Trade tool order quantity is a String at the ADK boundary

**Date:** 2026-10-26
**Status:** accepted

## Context

The Trade Executor sub-agent (see `2026-10-24-agent-trade-execution.md`) exposes
`proposeTrades` as an ADK `FunctionTool`. Its argument type, `TradeOrderInput`,
originally declared `quantity: BigDecimal`.

ADK generates the function-call JSON schema that Gemini sees from the Kotlin
parameter types. `BigDecimal` is not a primitive to ADK's schema generator, so it
was described as a nested **object** rather than a scalar. Gemini then produced a
structured object for `quantity`, and deserialization failed at runtime:

```
Cannot deserialize value of type java.math.BigDecimal from Object value
(token JsonToken.START_OBJECT) ... TradeOrderInput["quantity"]
```

The existing unit tests passed because they supplied scalar args directly,
bypassing the generated schema — so the bug only surfaced against a live model.

## Decision

`TradeOrderInput.quantity` is declared as `String`. The scalar maps to a clean
`type: string` schema, which the model fills reliably. The value is parsed to
`BigDecimal` immediately:

- `ProposeTradesTool` validates it (`toBigDecimalOrNull`, must be > 0) and reports
  a non-numeric value as an invalid order line.
- `TradeProposalService.propose` parses the validated string into the domain
  `BigDecimal` it stores on each `ProposedOrder`.

The domain representation stays `BigDecimal` everywhere beyond the tool boundary.
`String` (over `Double`) preserves exact precision for fractional-share
quantities.

## Consequences

- Do **not** change ADK `FunctionTool` parameter fields back to `BigDecimal` (or
  other non-primitive numeric types); they regenerate an object schema and break
  the same way.
- A schema-type assertion in `ProposeTradesToolTest` guards the `quantity` field
  staying `STRING`.
