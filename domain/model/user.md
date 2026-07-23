# User

## Overview

Represents a registered user of the paper trading platform. Holds identity and contact information, and acts as the owner of one or more trading accounts. Users may be created via manual registration or auto-registered during an OIDC login flow.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| id | uuid | yes | Unique user identifier |
| firstName | string | yes | User's first name |
| lastName | string | yes | User's last name |
| address | string | **no** | User's postal address. Optional — may be `null` for users auto-registered via an external identity provider. See decision `2026-07-23-user-address-optional`. |
| email | string | yes | User's email address, used for login and notifications. Unique across the platform. |
| status | enum | yes | `active` \| `suspended` \| `closed` |
| createdAt | datetime | yes | Timestamp of account creation |
| updatedAt | datetime | yes | Timestamp of last profile update |

## Behaviors

- **Register**: Creates a new user with `active` status. Validates that `email` is unique across the platform. Creates a default `UserSettings` row in the same transaction. `address` is accepted when supplied; stored as `null` when absent. Emits `UserRegistered`.
- **OidcRegister**: Triggered automatically during the OIDC login flow when no existing user is found for the provider's `subId` or email. Creates a user with `firstName` from the provider's `given_name`, `lastName` from `family_name`, `address = null`, and `email` from the ID token. Sets `status` to `active`. Creates a default `UserSettings` row and an `ExternalIdentityProvider` row in the same transaction. Emits `UserRegistered`.
- **Close**: Transitions status to `closed`. Irreversible; no further actions are permitted on the user.

## Events

- **UserRegistered**: Emitted after a successful registration (both manual and OIDC). Payload: `userId`, `email`, `timestamp`.

## Relationships

- **Account** (`one-to-many`): A user may own one or more paper trading accounts.
- **UserSettings** (`one-to-one`): Each user has exactly one settings record, created at registration with default values. Settings are returned inline with the user profile response.
- **ExternalIdentityProvider** (`one-to-many`): A user may have at most one row per provider type, linking the platform profile to the provider's identity.

## Business Rules

- `email` must be unique across all users.
- `email` must be a valid email format.
- `firstName` and `lastName` must not be empty.
- `address` is optional and may be `null`.
- `status` transitions are one-directional: `active` → `suspended` → `closed`. A closed user cannot be reopened.
- A suspended or closed user may not perform any trading actions.
- A `UserSettings` row must exist for every user. It is created atomically in the same transaction as the `User` record and is never absent.
