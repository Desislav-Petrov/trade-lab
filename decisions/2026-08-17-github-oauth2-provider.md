# Decision: Add GitHub as an OAuth2 Identity Provider

**Date:** 2026-08-17  
**Status:** Accepted

## Context

The `ExternalIdentityProvider` entity documents that `providerType` is currently `GOOGLE` only and that adding new values requires a decision log entry. This feature adds `GITHUB` as a second supported provider.

GitHub OAuth2 via Spring Security's built-in `github` registration differs from Google OIDC in the following ways relevant to this platform:

1. **No OIDC ID token.** GitHub uses OAuth2 (not OIDC). Spring Security fetches user info from `https://api.github.com/user`. There is no `sub` claim — the unique identifier is the numeric GitHub user ID exposed as the `id` field on the user-info response.
2. **Single `name` field.** GitHub does not return separate `given_name` / `family_name` claims. It returns a single `name` field (which may be `null`).
3. **Email may be private.** GitHub accounts may have no public email. In that case the user-info endpoint returns `null` for `email`.

## Decisions

### 1. `providerType` enum — add `GITHUB`

`ProviderType.GITHUB` is added to `org.dpp.tradelab.user.model.ProviderType`. No other change to the entity schema is required.

### 2. `subId` source for GitHub

The GitHub numeric user ID (the `id` field on the `/user` user-info response, exposed by Spring Security as the `name` attribute) is used as `subId`. This is stable, globally unique per GitHub account, and immutable.

### 3. Name splitting

GitHub's `name` field is split on the **first space**:
- If a space is found: `firstName` = everything before the first space, `lastName` = everything after.
- If no space is found (single token or `null`/blank): `firstName` = full value (or `"User"` if null/blank), `lastName` = `""`.

### 4. No public email — reject login

If the GitHub user-info response returns a `null` or blank `email`, the login is rejected before any find-or-register logic runs. The user is redirected to `{frontend-origin}/login?error=github_no_email`. The login page displays: _"Your GitHub account has no public email. Please make your email public on GitHub and try again."_

This is consistent with the platform's requirement that every `User` record has a non-null, unique `email`.

### 5. Error message specificity

OIDC failure error messages are now provider-specific:
- `?error=oidc_failed` from the Google path → _"Google authentication failed. Please try again."_
- `?error=github_oidc_failed` from the GitHub path → _"GitHub authentication failed. Please try again."_
- `?error=github_no_email` → _"Your GitHub account has no public email. Please make your email public on GitHub and try again."_
- `?error=server_error` (either path) → _"Something went wrong. Please try again."_

The frontend maps each error code to its specific message string.

### 6. Spring Security registration name

The standard Spring Security built-in `github` registration name is used. Authorization endpoint: `/oauth2/authorization/github`. Callback endpoint: `/login/oauth2/code/github`. No custom paths.

### 7. Configuration

GitHub client ID and secret are kept as environment variables:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ${GITHUB_CLIENT_ID:not-configured}
            client-secret: ${GITHUB_CLIENT_SECRET:not-configured}
            scope: read:user,user:email
```

### 8. `OidcAuthService.handleCallback()` reuse

The existing `handleCallback(providerType, subId, email, firstName, lastName)` method in `OidcAuthService` is called with `ProviderType.GITHUB`. No changes to its signature or logic are required. Name splitting and email validation are performed in the `OidcAuthenticationSuccessHandler` before calling the service, consistent with the Google path.

### 9. `OidcAuthenticationSuccessHandler` — provider detection

The handler inspects `authentication.authorizedClientRegistrationId` (available on the `OAuth2AuthenticationToken`) to determine which provider issued the callback, then branches accordingly to extract the correct claims.

## Alternatives Considered

- **Forcing GitHub through OIDC scope** — GitHub does support an OIDC flow under `https://token.actions.githubusercontent.com` but this is for GitHub Actions, not user login. Not applicable here.
- **Storing GitHub username as `subId`** — rejected; usernames are mutable. The numeric `id` is immutable.
