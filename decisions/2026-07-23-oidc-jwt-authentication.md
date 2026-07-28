# Decision: Backend-driven OIDC with internal HMAC JWT

**Date:** 2026-07-23  
**Status:** Accepted  
**Related issue:** #40

## Context

Issue #40 requires real authentication via external identity providers (starting with Google) and a stateless session token for all subsequent API requests. The naive email-selection login is retained for testing only. The issue explicitly requires Spring OAuth2 integration and `io.jsonwebtoken` with HMAC.

## Decisions

### OAuth2 / OIDC flow

Backend-driven OAuth2 using Spring Security's OAuth2 Login (`spring-boot-starter-oauth2-client`). Spring manages:
- The redirect to Google (`/oauth2/authorization/google`).
- The callback from Google (`/login/oauth2/code/google`).
- Code-for-token exchange with Google's token endpoint.
- Extraction of claims from the ID token.

A custom `OidcAuthenticationSuccessHandler` is registered. After Spring validates the Google callback it:
1. Extracts `sub`, `email`, `given_name`, `family_name` from the `OidcUser` principal.
2. Delegates find-or-register logic to `OidcAuthService`.
3. Issues an internal JWT via `JwtService`.
4. Redirects the browser to `{frontend-origin}/auth/callback?token={jwt}`.

On any failure a custom `OidcAuthenticationFailureHandler` redirects to `{frontend-origin}/login?error={code}`.

### Internal JWT format

Issued with `io.jsonwebtoken` (jjwt), signed with HMAC-SHA256 (`HS256`).

```json
{
  "sub": "<userId-as-string>",
  "iat": "<unix-epoch-seconds>",
  "exp": "<iat + 86400>",
  "iss": "trade-platform"
}
```

Validity: 24 hours. No refresh tokens in this iteration.

### HMAC secret

Configured via `application.yml` under `app.jwt.secret`. Must be a minimum 256-bit (32-byte) string. In CI/production it is injected via environment variable; in development the `application.yml` carries a hard-coded default (acceptable for in-memory H2 with no real data).

### JWT validation

A `JwtAuthenticationFilter` (`OncePerRequestFilter`) sits in the Spring Security filter chain ahead of all controllers. It:
1. Reads the `Authorization: Bearer {token}` header.
2. Validates signature, `exp`, and `iss` via `JwtService`.
3. Extracts `sub` as `userId` (UUID).
4. Sets a `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`.
5. Returns HTTP 401 on any validation failure.

### CORS

A global CORS configuration allows requests from the configured frontend origin (`app.cors.allowed-origin`). `Authorization` is explicitly included in `allowedHeaders`.

### Public endpoints

The following paths bypass JWT validation:
- `POST /api/v1/users/register` — manual registration (retained for testing)
- `GET /api/v1/users` — email list (retained for testing)
- `/oauth2/authorization/**` — Spring OAuth2 redirect trigger
- `/login/oauth2/code/**` — Spring OAuth2 callback

All other endpoints require a valid internal JWT.

### Email deduplication across providers

On callback: look up `ExternalIdentityProvider` by `(providerType, subId)`. If absent, look up `User` by email. If a user with that email exists, create a new `ExternalIdentityProvider` row linked to that user — no new `User` is created. If no user exists, create `User` + `ExternalIdentityProvider`. This ensures one profile per email regardless of the number of providers used.

### Frontend session persistence

The internal JWT and the user profile are stored in `localStorage`. On application load, the session store attempts to restore from `localStorage`; if the stored token is expired (checked by decoding the `exp` claim client-side), the session is not restored and the user is redirected to `/login`.

## Consequences

- `spring-boot-starter-oauth2-client` and `spring-boot-starter-security` added as dependencies.
- `io.jsonwebtoken` (jjwt) added as a dependency.
- All non-public endpoints require a valid Bearer JWT; HTTP 401 returned otherwise.
- `Session` model gains `accessToken` field and localStorage persistence rule.
- A new `ExternalIdentityProvider` entity is introduced in the User domain.
- `User.address` is made optional (see `2026-07-23-user-address-optional.md`).
