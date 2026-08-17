# ExternalIdentityProvider

## Overview

Maps an external identity provider account to a platform `User`. Created the first time a user authenticates via an external provider (e.g. Google, GitHub). Each row represents one provider-specific identity linked to one platform user profile. A single `User` may have multiple rows — one per provider — but at most one row per `(userId, providerType)` pair.

The `ExternalIdentityProvider` entity lives in the User domain.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| id | uuid | yes | Unique identifier |
| userId | uuid | yes | Reference to the platform `User` this provider identity is linked to |
| providerType | enum | yes | `GOOGLE` \| `GITHUB` — further values (`FACEBOOK`) may be added in future iterations with a corresponding decision log entry |
| subId | string | yes | The unique identifier issued by the provider for this user. For `GOOGLE`: the `sub` claim in the OIDC ID token. For `GITHUB`: the numeric GitHub user ID (the `id` field on the `/user` user-info response). Immutable after creation. |
| email | string | yes | Email address returned by the provider at first authentication. Stored for audit; not used for deduplication after the initial registration. |
| lastAccessedAt | datetime | yes | Timestamp of the most recent successful authentication via this provider. Updated on every successful OAuth2 callback for this record. |

## Behaviors

- **Link**: Creates a new `ExternalIdentityProvider` row the first time a `(providerType, subId)` combination is seen. `userId` is determined by the find-or-register sub-flow in `OidcAuthService`. `lastAccessedAt` is set to the current timestamp.
- **UpdateLastAccessed**: On every subsequent successful OAuth2 callback for an existing record, updates `lastAccessedAt` to the current timestamp.

## Events

_None emitted directly._

## Relationships

- **User** (`many-to-one`): Each row belongs to exactly one `User`. A user may have at most one row per `providerType`.

## Business Rules

- `(providerType, subId)` is a unique constraint.
- `(userId, providerType)` is a unique constraint. A user may link at most one account per provider.
- `subId` is immutable after creation.
- `providerType` is immutable after creation.
- Email deduplication rule: if `(providerType, subId)` is not found but a `User` with the same email already exists, a new row is created linking the existing user to this provider. No new `User` record is created.
- **GitHub-specific:** If the GitHub user-info response returns a null or blank `email`, the login is rejected before any find-or-register logic runs. A `User` record must have a non-null email — this is a hard requirement. See `decisions/2026-08-17-github-oauth2-provider.md`.
- **GitHub-specific:** The GitHub `name` field is split on the first space to derive `firstName` and `lastName`. If no space is present (or `name` is null/blank), `firstName` = full value (or `"User"` if null/blank), `lastName` = `""`. This splitting is performed in the success handler before calling `OidcAuthService`.
- Adding a new `providerType` enum value requires a corresponding decision log entry.
