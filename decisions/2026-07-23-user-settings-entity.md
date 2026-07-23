# Decision: Introduce UserSettings as a User Domain Sub-Entity with Cross-Domain Event

**Date:** 2026-07-23
**Status:** accepted

## Context

Issue #112 requires a per-user configurable feed type preference (`SYNTHETIC` | `REAL`). The preference must be persisted, returned inline with the user profile, and propagated to the Market Data domain so it can route the correct feed per connected user. Additional user-level settings are anticipated in future iterations.

## Decision

1. **New sub-entity `UserSettings`**: A dedicated `user_settings` table is introduced, owned by the User domain, with a mandatory one-to-one relationship to `User`. It is created automatically at user registration with default values. New settings fields are added to this table in future iterations without modifying the `users` table.

2. **Settings returned inline with user profile**: The existing user profile endpoint extends its response to include the nested `settings` object. No separate fetch is required by the frontend.

3. **New PATCH endpoint for settings**: A dedicated `PATCH /api/v1/users/{userId}/settings` endpoint is introduced. It is designed to be extensible — the request body is a partial settings object supporting any subset of settings fields. This endpoint belongs to the User domain.

4. **New cross-domain event `UserSettingsChangedEvent`**: Emitted by the User domain after any successful settings update. Payload carries `userId`, the full settings snapshot (all fields), and `lastUpdated`. This full-snapshot pattern mirrors the `OrderFilledEvent` convention already in use and avoids downstream consumers needing to track deltas.

5. **Market Data feed-type cache**: The Market Data domain maintains an in-memory `feedType` cache keyed by `userId`. It is seeded from the database at application startup (single bulk read of all `UserSettings` rows). It is kept current via `UserSettingsChangedEvent`. The WebSocket feed component consults this cache at connection time and on each tick dispatch to determine whether to push synthetic or real data to a given user.

6. **No reconnect required on feed type change**: The feed type switch takes effect on the existing WebSocket connection. Eventual consistency is acceptable — a user may receive a few ticks from the old feed type while the event propagates and the cache is updated.

7. **Real feed not implemented in this iteration**: Selecting `REAL` persists the preference and updates the cache, but the backend has no real data source in this iteration. The feed routing logic is structured so the real feed can be plugged in without changing the dispatch interface.

## Consequences

- `domain/model/user.md` must be updated to add the `settings` one-to-one relationship.
- `domain/flows/user-registration.md` must be updated to include `UserSettings` creation at registration time.
- `domain/flows/market-data-websocket-feed.md` must be updated to document feed-type routing logic in Flows A and B.
- The `Session` frontend model will need to include `settings` in a future iteration when settings are persisted across page refreshes. Out of scope here — the settings are re-fetched from the backend on each login.
- Future settings additions (e.g. notification preferences, theme) add fields to `UserSettings` and extend the `PATCH` endpoint body. No new tables or endpoints are needed unless a setting warrants its own lifecycle.
