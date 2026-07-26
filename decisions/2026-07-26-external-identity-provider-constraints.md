# External Identity Provider Constraints

**Date:** 2026-07-26  
**Status:** Decided

## Problem

When designing `ExternalIdentityProvider` entity to map OIDC provider accounts to platform users, two design questions emerged:

1. **Unique constraints:** Should we have two separate unique constraints on `(provider_type, sub_id)` and `(user_id, provider_type)`?
2. **Model structure:** Is the current entity design (direct id, userId relationship) the best approach?

## Decision

### Two Unique Constraints — Both Required

Yes, both constraints are necessary and cover distinct edge cases:

#### Constraint 1: `(provider_type, sub_id)` UNIQUE
- **Purpose:** Prevent linking the same external identity (e.g., `google:user123`) to multiple platform users
- **Edge case covered:** Protects against authentication vulnerabilities where a user could claim ownership of someone else's external account
- **Example:** If user A registers with Google, they receive a `(GOOGLE, sub_12345)` record. User B cannot later authenticate with the same Google account and get assigned to themselves.
- **Why it's necessary:** OIDC guarantees that a `sub` is unique per provider and user

#### Constraint 2: `(user_id, provider_type)` UNIQUE
- **Purpose:** Prevent a single platform user from linking two different accounts from the same provider
- **Edge case covered:** Prevents user confusion and account hijacking scenarios where a user accidentally (or maliciously) links multiple Google accounts to the same platform user
- **Example:** User registers with `google:alice@gmail.com`, receives a record `(user-123, GOOGLE, alice_sub)`. They cannot later link `google:alice-alt@gmail.com` to user-123; they would need a separate platform user for that
- **Why it's necessary:** Simplifies account management and prevents credential confusion

### Model Structure — Intentional Design

The current model is correct:

```kotlin
@Entity
class ExternalIdentityProvider(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID,  // Primary key — enables querying by provider record id
    
    @Column(nullable = false, updatable = false)
    val userId: UUID,  // Foreign reference to User — stored as UUID only
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val providerType: ProviderType,  // e.g. GOOGLE
    
    @Column(nullable = false, updatable = false)
    val subId: String,  // OIDC 'sub' claim — provider's unique identifier for the user
    
    @Column(nullable = false)
    val email: String,  // Email from OIDC provider — may differ from User.email
    
    @Column(nullable = false)
    var lastAccessedAt: Instant  // Track last successful OIDC login
)
```

**Why each field is necessary:**

- **`id: UUID`** — JPA requirement for `@Id`, enables direct queries on provider records (e.g. "get the provider record with this UUID")
- **`userId: UUID`** — References the user this provider is linked to. Stored as UUID only (not an `@ManyToOne` entity relationship) to avoid cross-domain entity dependencies per architecture standards
- **`providerType`** — Distinguishes between Google, GitHub, Microsoft, etc. Allows future multi-provider support
- **`subId`** — The "subject" claim from OIDC — the provider's opaque unique identifier for this end-user. Must not be exposed to the user; used only for authentication
- **`email`** — Captured at OIDC callback; may differ from the `User.email` if the user later changed their email in the platform. Useful for audit trails and debugging
- **`lastAccessedAt`** — Enables tracking inactive provider links; could be used to warn users about unused social logins or for analytics

## Trade-offs and Rationale

### Why not a composite primary key `(provider_type, sub_id)`?

While it's tempting to make `(provider_type, sub_id)` the primary key, we kept a separate `UUID id` because:

1. **Queryability:** Allows callers to query the provider record directly by its UUID without knowing provider type and sub_id
2. **Relationship stability:** The UUID is stable; sub_id is an opaque string that could theoretically change (though OIDC specs say it won't)
3. **Event publishing:** Domain events can include the provider record UUID, making events more self-documenting

### Why store `email` in the provider record?

Even though `User.email` exists, storing it here provides:
- Audit trail of what email the provider reported at authentication time
- Faster lookups during callback (no need to join User table)
- Historical record if the user later changes their platform email

## Implementation

- Unique constraint 1 (provider + sub): `UniqueConstraint(columnNames = ["provider_type", "sub_id"])`
- Unique constraint 2 (user + provider): `UniqueConstraint(columnNames = ["user_id", "provider_type"])`
- Queries:
  - `findByProviderTypeAndSubId(providerType, subId)` — find or create flow
  - `findByUserIdAndProviderType(userId, providerType)` — account unlinking

## Future Extensions

When adding a second OIDC provider (e.g., GitHub):
1. Add `GITHUB` to `ProviderType` enum
2. Update `application.yml` with GitHub OAuth2 config
3. No schema changes required; existing constraints and columns handle multiple providers
4. UI can show all linked providers: `select * from external_identity_providers where user_id = ?`
