# Decision: ENABLE_NO_AUTH Environment Flag Gates Email Login and Registration

**Date:** 2026-08-30  
**Status:** accepted

## Context

The email-select login (`domain/flows/user-login`) and manual email registration (`domain/flows/user-registration`) flows exist solely for local development convenience. Neither performs any credential check. Exposing these endpoints and routes in a production deployment is a security risk: any visitor could log in as any registered user or create arbitrary accounts with no authentication.

The platform already uses OIDC (Google, GitHub) as its primary authentication path. In production, only OIDC flows should be reachable.

A Spring profile-only gate was considered but rejected because the frontend also needs to suppress the corresponding routes and UI entry points. A shared environment variable (`ENABLE_NO_AUTH`) gives both layers a single, unambiguous signal without coupling the frontend to Spring-specific concepts.

## Decision

A boolean environment variable named `ENABLE_NO_AUTH` controls both layers:

- **Backend:** The email login endpoint (`POST /api/v1/users/login`) and the email registration endpoint (`POST /api/v1/users`) are only registered when `ENABLE_NO_AUTH=true`. When the variable is absent or `false`, the Spring application does not expose these endpoints — they are not reachable at all (no 404, no 403; the routes simply do not exist).
- **Frontend:** The `/login` and `/register` routes are only included in the React router when `ENABLE_NO_AUTH=true` at build time. When absent or `false`, navigating to either path produces a blank / not-found result — the routes do not exist in the bundle.

The variable defaults to `false` (disabled) when absent, making the safe state the default.

## Consequences

- `domain/flows/user-login` is updated to add `ENABLE_NO_AUTH=true` as a precondition.
- `domain/flows/user-registration` is updated to add `ENABLE_NO_AUTH=true` as a precondition.
- The backend must conditionally register the two endpoints based on this flag (e.g. via a `@ConditionalOnProperty` or profile-guarded `@Bean`).
- The frontend must read `VITE_ENABLE_NO_AUTH` (Vite's build-time env prefix) and conditionally include the `/login` and `/register` routes.
- Production deployments must not set `ENABLE_NO_AUTH=true`. Local and CI dev environments set it to `true`.
- Any future no-credential-check flow must be gated by the same flag.
