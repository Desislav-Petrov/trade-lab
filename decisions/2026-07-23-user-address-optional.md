# Decision: Make User.address optional to support OIDC auto-registration

**Date:** 2026-07-23  
**Status:** Accepted  
**Related issue:** #40

## Context

The `User` entity previously treated `address` as a required field, enforced in both the domain model and the manual registration flow. External identity providers (Google, GitHub, etc.) supply `given_name`, `family_name`, and `email` from their ID tokens — they do not supply a postal address. Auto-registering a user profile during the OIDC callback is impossible without either blocking the flow to collect an address (out of scope) or relaxing the constraint.

## Decision

`User.address` is now optional (nullable).

- Manual registration retains the `address` input field; it is accepted when supplied and stored as-is.
- OIDC-registered users have `address = null` at creation time.
- A future profile-edit use case may allow the user to supply an address.
- All validation logic that previously treated an absent `address` as an error is updated to permit `null`.

## Consequences

- `users.address` column becomes nullable in the database schema.
- `User` entity updated: `address: String?`.
- `user-registration.md` updated: `address` removed from required-field validation step.
- No data migration required (H2 in-memory, `ddl-auto: create-drop`).
