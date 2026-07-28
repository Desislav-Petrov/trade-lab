# Use Case: Authenticate with External Identity Provider

## Goal

A guest authenticates using their Google account and establishes a session on the paper trading platform. If no platform profile exists for the guest, one is automatically created from the identity provider's claims.

## Actor

Guest — an unauthenticated visitor with a Google account.

## Screen

- **Route:** `/login` — entry point; displays the "Login with Google" button.
- **Route:** `/auth/callback` — redirect target after the Google callback; reads the token and establishes the session.
- **Pages:** `LoginPage`, `AuthCallbackPage`

## Trigger

Guest clicks "Login with Google" on the `/login` page.

## Domain Models

- `domain/model/user`
- `domain/model/external-identity-provider`
- `domain/model/session`

## Flows

- `domain/flows/oidc-login`
- `domain/flows/jwt-authentication`

## Happy Path

1. Guest clicks "Login with Google" on `/login`.
2. Browser redirects to `/oauth2/authorization/google` → Spring Security redirects to Google.
3. Guest authenticates with Google and grants consent.
4. Google redirects to Spring's callback URL. Spring validates the authorisation code, exchanges it for an ID token, and extracts `sub`, `email`, `given_name`, `family_name`.
5. Backend finds or auto-creates the user profile and links the Google provider identity.
6. Backend issues a 24h internal JWT (`{ sub: userId, iat, exp, iss: "trade-platform" }`) and redirects to `/auth/callback?token={jwt}`.
7. Frontend reads the token from the query param, stores it in `localStorage`, fetches the full user profile, and populates the Zustand session store.
8. Guest is redirected to `/trade`. All subsequent API requests carry `Authorization: Bearer {token}` via the Axios interceptor.

## Failure Scenarios

| Scenario | Trigger | UI Outcome |
|----------|---------|------------|
| Google auth denied | Guest denies consent or Google returns an error | Redirect to `/login?error=oidc_failed`. Login page shows "Authentication failed. Please try again." |
| Server error during find-or-register | Unexpected backend exception | Redirect to `/login?error=server_error`. Login page shows a generic error message. |
| Profile fetch fails after callback | `GET /api/v1/users/{userId}` fails | Guest remains on `/auth/callback` with a generic error message. Session not established. |
| JWT expired on page load | Stored JWT has passed its 24h validity | Session not restored on load. Guest redirected to `/login`. |
| Protected endpoint called without valid JWT | Any subsequent request with missing, malformed, or expired token | HTTP 401 returned. Frontend redirects to `/login`. |

## Out of Scope

- GitHub, Facebook, or other identity providers (architecture supports them; implementation deferred).
- Refresh tokens.
- Manual profile completion for OIDC-registered users (`address` remains `null` until a future profile-edit use case).
- Password-based authentication.
- Multi-factor authentication.
- Account recovery.
- Role-based authorisation (any authenticated user may access all endpoints).
