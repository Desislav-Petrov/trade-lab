# Decision: Email-Select Login Issues Internal JWT

**Date:** 2026-08-30  
**Status:** accepted

## Context

The email-select login flow (`domain/flows/user-login`) was a local-testing shortcut that established a backend session and returned `userId` + `email` directly. It did not issue a JWT. As the platform matured, all protected endpoints were secured with JWT Bearer authentication (`domain/flows/jwt-authentication`). This meant that any user authenticated via the email-select path could not make authenticated API calls — their requests would be rejected with HTTP 401.

## Decision

The email-select flow is updated to issue the same internal JWT (`{ sub: userId, iat, exp: iat+86400, iss: "trade-platform" }` signed with HMAC-SHA256) that the OIDC flows issue. The post-login frontend behaviour now mirrors the OIDC callback exactly: the backend redirects to `{frontend-origin}/auth/callback?token={jwt}`, and the frontend reads the token from the query parameter, stores it in `localStorage`, fetches the full user profile, and populates the Zustand session store.

The email-select mechanism itself (no password, no credential check) is unchanged. This remains a local-testing convenience only and must never be enabled in production.

## Consequences

- `domain/flows/user-login` is rewritten to reflect JWT issuance and the OIDC-mirrored frontend callback path.
- `domain/usecases/login-user` is updated to reflect the new happy path and failure scenarios.
- The backend login endpoint must now call `JwtService` and redirect to `{frontend-origin}/auth/callback?token={jwt}` instead of returning a JSON body with `userId`/`email`.
- The frontend email-select login path must be updated to expect the `/auth/callback` redirect rather than handling a JSON response directly. The existing `AuthCallbackPage` handles the rest.
- The public-endpoints whitelist in `SecurityConfig` must continue to include the email-select login endpoint.
- `POST /api/v1/users/login` (or equivalent) no longer returns a JSON body — it returns a redirect, consistent with the OAuth2 success handler pattern.
