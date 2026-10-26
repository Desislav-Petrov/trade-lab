# Decision: Market-data WebSocket feed JSON is serialised with a dedicated Jackson mapper

**Date:** 2026-10-26
**Status:** accepted
**Relates to:** GitHub issue #240

## Context

`MarketDataFeedService` built its outbound WebSocket messages (`TICK` and
`SNAPSHOT`) by hand via string concatenation. The hand-written `escapeJson`
helper only escaped `\\` and `"`. Control characters (newline, tab, etc.) in
string fields such as `companyName` were emitted unescaped, producing invalid
JSON that breaks the client parser.

The backend standards do not cover WebSocket wire-format serialisation — only
REST DTOs (which are generated from OpenAPI). A decision is therefore required.

## Decision

1. Outbound WebSocket messages are serialised with Jackson instead of by hand.
   Jackson guarantees valid JSON regardless of field contents, including correct
   escaping of all control characters.

2. The service uses a **dedicated, locally-configured `ObjectMapper`** rather
   than injecting Spring's shared bean. Rationale:
   - The WebSocket wire format must stay deterministic and byte-stable. A private
     mapper keeps it immune to global Jackson configuration changes elsewhere in
     the application.
   - It avoids changing the service constructor signature, keeping the blast
     radius of this fix to one file.
   - `WRITE_BIGDECIMAL_AS_PLAIN` is enabled so prices serialise as plain JSON
     numbers (e.g. `150.000`) with no exponent notation.

3. The existing wire format is preserved exactly: envelope shapes
   `{"type":"TICK","data":{…}}` / `{"type":"SNAPSHOT","data":[…]}`, field order
   (`ticker, companyName, currentPrice, open, dayLow, dayHigh, fiftyTwoWeekHigh`)
   via an ordered `LinkedHashMap`, prices scaled to exactly 3 decimal places, and
   `updatedAt` excluded. Field order is enforced only to keep the format stable
   and existing tests green — JSON object key order is not semantically meaningful.

## Consequences

- The hand-written `escapeJson` helper is removed.
- Any future field added to the feed payload must be added to `snapshotToMap`.
- If the feed format ever needs to reuse the shared application `ObjectMapper`,
  this decision must be revisited.
