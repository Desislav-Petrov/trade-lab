# JWT Authentication

## Overview

Covers how all protected API endpoints are authenticated after a successful OIDC login. The frontend attaches the internal JWT as a Bearer token on every request. A filter in the Spring Security chain validates the token and makes the authenticated `userId` available to the controller and service layers.

This is a cross-cutting flow — it applies to every protected endpoint in every domain.

## Actors

- **Authenticated User**: A guest who has completed the OIDC login flow and holds a valid JWT in `localStorage`.
- **Guest Browser**: The React frontend attaching the token via the shared Axios request interceptor.
- **System**: The Spring Boot backend — `JwtAuthenticationFilter`, `JwtService`, `SecurityContextHolder`.

## Preconditions

- The user has completed the OIDC login flow and the JWT is stored in `localStorage`.
- The endpoint being called is not in the public-endpoints whitelist.

## Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest Browser | Attach token | The Axios request interceptor reads `accessToken` from `localStorage` and adds `Authorization: Bearer {token}` to every outgoing request. |
| 2 | System | Intercept request | `JwtAuthenticationFilter` (`OncePerRequestFilter`) intercepts the request before it reaches any controller. |
| 3 | System | Extract token | Reads the `Authorization` header. If the header is absent or does not start with `Bearer `, the filter sets no authentication and the request continues — Spring Security's access rules will return 401 for any protected endpoint reached in this state. |
| 4 | System | Validate token | Parses the JWT via `JwtService`. Validates: HMAC-SHA256 signature, `exp` (not expired), `iss` (equals `"trade-platform"`). |
| 5 | System | Extract userId | Reads the `sub` claim and parses it as a `UUID`. |
| 6 | System | Set SecurityContext | Creates a `UsernamePasswordAuthenticationToken` with the userId as principal and sets it in `SecurityContextHolder`. |
| 7 | System | Proceed to controller | The request continues to the controller/service. Services may call `SecurityContextHolder.getContext().authentication.principal` to retrieve the userId as a `UUID`. |

## Postconditions

- The `SecurityContextHolder` contains the authenticated userId for the duration of the request.
- The controller/service layer can retrieve the userId without an additional database query.

## Error Cases

| Scenario | Condition | System Response |
|----------|-----------|------------------|
| No Authorization header | Request reaches a protected endpoint with no `Authorization` header | Filter sets no authentication; Spring Security returns HTTP 401 |
| Malformed token | Header present but value is not a valid JWT | `JwtService` throws; filter catches and returns HTTP 401 |
| Invalid signature | Token tampered with | `JwtService` throws `SignatureException`; filter returns HTTP 401 |
| Expired token | `exp` is in the past | `JwtService` throws `ExpiredJwtException`; filter returns HTTP 401 |
| Issuer mismatch | `iss` ≠ `"trade-platform"` | `JwtService` throws; filter returns HTTP 401 |

## Domain Models Involved

_None — this flow is a cross-cutting security concern, not bound to a single domain model._

## Notes

- Public endpoints (`POST /api/v1/users/register`, `GET /api/v1/users`, `/oauth2/authorization/**`, `/login/oauth2/code/**`) bypass this filter entirely — they are whitelisted in `SecurityConfig`.
- The filter does not query the database. Token validity is determined entirely from the JWT claims and the HMAC secret.
