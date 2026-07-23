# UserSettings

## Overview

Represents the persistent platform-level preferences for a single user, owned by the User domain. Stored in a dedicated `user_settings` table with a mandatory one-to-one foreign key to the `users` table. A `UserSettings` row is created automatically at user registration with default values and is never deleted independently of the user. Settings are returned inline with the user profile and can be updated via a dedicated PATCH endpoint. Any change emits a `UserSettingsChangedEvent` carrying the full settings snapshot so downstream domains (e.g. Market Data) can react without querying the database.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| id | uuid | yes | Unique settings record identifier |
| userId | uuid | yes | Foreign key reference to the owning `User`. Unique constraint — one settings row per user. |
| feedType | enum | yes | The market data feed type the user has chosen: `SYNTHETIC` \| `REAL`. Defaults to `SYNTHETIC` at creation. In this iteration, selecting `REAL` persists the preference but the backend continues to serve synthetic data until a real feed integration is built. |
| updatedAt | datetime | yes | Timestamp of the last settings update. Set to the current timestamp on creation and on every PATCH. |

## Behaviors

- **CreateDefaults**: Called atomically within the user registration transaction. Creates a `UserSettings` row with `feedType: SYNTHETIC` and `updatedAt` set to the current timestamp. No event is emitted at creation.
- **UpdateFeedType**: Updates `feedType` to the supplied value and sets `updatedAt` to the current timestamp. Emits `UserSettingsChangedEvent` with the full settings snapshot.

## Events

- **UserSettingsChangedEvent**: Emitted after any successful settings update via the PATCH endpoint. Payload: `userId`, `feedType`, `updatedAt`. The payload is a full snapshot of all current settings fields — consumers do not need to track which field changed. Future settings additions extend the payload.

## Relationships

- **User** (`many-to-one`, unique): Each `UserSettings` row belongs to exactly one `User`. `userId` carries a unique constraint — at most one settings row exists per user at any time.

## Business Rules

- A `UserSettings` row must exist for every registered user. It is created in the same transaction as the `User` record during registration.
- `feedType` must be one of the supported enum values: `SYNTHETIC` or `REAL`. Unknown values are rejected with HTTP 400.
- The default `feedType` for all new users is `SYNTHETIC`.
- `updatedAt` is always set to the current server timestamp on creation and on every update. It is never supplied by the client.
- Settings are returned as a nested `settings` object in every user profile response. Clients do not need a separate fetch.
- The PATCH endpoint accepts a partial settings object. Only fields present in the request body are updated. Fields absent from the body are left unchanged.
- A PATCH with the same value as the current setting is a valid no-op: the record is updated (`updatedAt` refreshed) and the event is still emitted.
- `UserSettingsChangedEvent` always carries the full snapshot of all settings fields, regardless of which field was changed.
- New settings fields are added to this entity and to the event payload. Adding a new field requires a corresponding decision log entry.
- The `UserSettings` table is read in bulk at Market Data service startup to seed the feed-type cache. The cache is the runtime source of truth for feed routing; the database is not queried per-request.
