# Use Case: Authenticate with External Identity Provider

## Goal

A guest authenticates using their Google or GitHub account and establishes a session on the paper trading platform. If no platform profile exists for the guest, one is automatically created from the identity provider's claims.

## Actor

Guest — an unauthenticated visitor with a Google or GitHub account.

## Screen

- **Route:** `/login` — entry point; displays "Login with Google" and "Login with GitHub" buttons.
- **Route:** `/auth/callback` — redirect target after the provider callback; reads the token and establishes the session.
- **Pages:** `LoginPage`, `AuthCallbackPage`

## Trigger

Guest clicks "Login with Google" or "Login with GitHub" on the `/login` page.

## Domain Models

- `domain/model/user`
- `domain/model/external-identity-provider`
- `domain/model/session`

## Flows

- `domain/flows/oidc-login` (Google path)
- `domain/flows/github-oidc-login` (GitHub path)
- `domain/flows/jwt-authentication`

## Happy Path — Google

1. Guest clicks "Login with Google" on `/login`.
2. Browser redirects to `/oauth2/authorization/google` → Spring Security redirects to Google.
3. Guest authenticates with Google and grants consent.
4. Google redirects to Spring's callback URL. Spring validates the authorisation code, exchanges it for an ID token, and extracts `sub`, `email`, `given_name`, `family_name`.
5. Backend finds or auto-creates the user profile and links the Google provider identity.
6. Backend issues a 24h internal JWT and redirects to `/auth/callback?token={jwt}`.
7. Frontend reads the token, stores it in `localStorage`, fetches the full user profile, and populates the Zustand session store.
8. Guest is redirected to `/trade`.

## Happy Path — GitHub

1. Guest clicks "Login with GitHub" on `/login`.
2. Browser redirects to `/oauth2/authorization/github` → Spring Security redirects to GitHub.
3. Guest authenticates with GitHub and grants consent.
4. GitHub redirects to Spring's callback URL. Spring exchanges the authorisation code for an access token and fetches user info from `https://api.github.com/user`. Extracts `id` (numeric, used as `subId`), `email`, `name`.
5. Backend validates that `email` is non-null and non-blank. Splits `name` on first space to derive `firstName` / `lastName`.
6. Backend finds or auto-creates the user profile and links the GitHub provider identity.
7. Backend issues a 24h internal JWT and redirects to `/auth/callback?token={jwt}`.
8. Frontend reads the token, stores it in `localStorage`, fetches the full user profile, and populates the Zustand session store.
9. Guest is redirected to `/trade`.

## Failure Scenarios

| Scenario | Trigger | UI Outcome |
|----------|---------|------------|
| Google auth denied | Guest denies consent or Google returns an error | Redirect to `/login?error=oidc_failed`. Login page shows "Google authentication failed. Please try again." |
| GitHub auth denied | Guest denies consent or GitHub returns an error | Redirect to `/login?error=github_oidc_failed`. Login page shows "GitHub authentication failed. Please try again." |
| GitHub no public email | GitHub user-info `email` is null or blank | Redirect to `/login?error=github_no_email`. Login page shows "Your GitHub account has no public email. Please make your email public on GitHub and try again." |
| Server error during find-or-register (either provider) | Unexpected backend exception | Redirect to `/login?error=server_error`. Login page shows "Something went wrong. Please try again." |
| Profile fetch fails after callback | `GET /api/v1/users/{userId}` fails | Guest remains on `/auth/callback` with a generic error message. Session not established. |
| JWT expired on page load | Stored JWT has passed its 24h validity | Session not restored on load. Guest redirected to `/login`. |
| Protected endpoint called without valid JWT | Any subsequent request with missing, malformed, or expired token | HTTP 401 returned. Frontend redirects to `/login`. |

## Out of Scope

- Facebook or other identity providers (architecture supports them; implementation deferred).
- Refresh tokens.
- Manual profile completion for OIDC-registered users (`address` remains `null` until a future profile-edit use case).
- Password-based authentication.
- Multi-factor authentication.
- Account recovery.
- Role-based authorisation (any authenticated user may access all endpoints).
