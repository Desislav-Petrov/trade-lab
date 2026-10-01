# Decision: JWT authentication for the market-data WebSocket

**Date:** 2026-10-01
**Status:** Accepted
**Related issue:** #201

## Context

The market-data WebSocket currently identifies users with a `userId` query parameter. The existing internal JWT can authenticate the connection without exposing a user identifier in the URL. Native browser WebSockets do not allow setting an `Authorization` header.

## Decisions

1. Reuse the existing internal JWT. The browser sends it through the `Sec-WebSocket-Protocol` header as the second requested subprotocol after the fixed `bearer` marker; do not introduce a new credential.
2. Derive identity solely from the validated JWT `sub` claim. Remove the `userId` query parameter.
3. Validate once after the WebSocket upgrade in `afterConnectionEstablished`. This preserves the existing `4401` close code and frontend error handling. A connection remains open until disconnected, even when the JWT expires during the session; mid-connection expiry enforcement is future hardening.
4. Keep shared JWT functionality in the independent `common` shared-kernel domain. Move `JwtService` and `InvalidTokenException` into `common.service` and `common.exception`, respectively, so any domain—including Market Data—can import the service directly. `common` depends on no other domain.

This shared-kernel approach supersedes the earlier proposal to expose JWT validation through `user.api.TokenValidationApi`.

## Consequences

- The WebSocket configuration negotiates and echoes the fixed `bearer` subprotocol so browser clients complete the upgrade.
- Missing, malformed, invalid, and expired tokens close the connection with code `4401`.
- The existing JWT validation machinery remains the source of signature, expiration, issuer, and subject validation.
- The `common` shared-kernel exception to the cross-domain service import rule is documented in `standards/architecture.md`.
