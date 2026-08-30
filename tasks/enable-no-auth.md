# Tasks: ENABLE_NO_AUTH — Gate Email Login and Registration (Issue #176)

## Context

The email-select login (`domain/flows/user-login`) and manual email registration (`domain/flows/user-registration`) flows are local-testing conveniences only. Neither performs any credential check. This task set gates both flows behind an `ENABLE_NO_AUTH` environment variable so they cannot be reached in production.

**Decision log:** `decisions/2026-08-30-enable-no-auth-flag.md`  
**Flows updated:** `domain/flows/user-login.md`, `domain/flows/user-registration.md`

---

## Dependency order

```
SVC-1  ──► CONTROLLER-1
       ──► CONTROLLER-2
SCREEN-1  (independent)
```

---

## SVC-1 — Add `app.features.enable-no-auth` property binding

**Layer:** Config / Resources  
**Domain:** user  
**Flows:** `user-login`, `user-registration`

### What to change

- `services/backend/src/main/resources/application.yml`  
  Under the existing `app.features` block, add:
  ```yaml
  app:
    features:
      enable-no-auth: ${ENABLE_NO_AUTH:true}
  ```
  Default is `true` so local dev works unchanged without setting the variable.

- `services/backend/src/main/resources/application-prod.yml`  
  Add a hard override — no env var escape:
  ```yaml
  app:
    features:
      enable-no-auth: false
  ```

### Acceptance criteria

- [ ] `application.yml` contains `app.features.enable-no-auth: ${ENABLE_NO_AUTH:true}`
- [ ] `application-prod.yml` contains `app.features.enable-no-auth: false` with no env var interpolation
- [ ] No other files are changed in this task
- [ ] `./gradlew build` passes

### Depends on
none

---

## CONTROLLER-1 — Conditionally register the no-auth user delegate

**Layer:** Controller  
**Domain:** user  
**Flows:** `user-login`, `user-registration`

### What to change

`services/backend/src/main/kotlin/org/dpp/tradelab/user/controller/UserApiDelegateImpl.kt`

The current `UserApiDelegateImpl` implements `UsersApiDelegate` and handles **all** user operations including `getUserById` and `updateUserSettings` — which must remain always-present. Only `registerUser`, `loginUser`, and `getActiveUserEmails` are gated.

**Split the delegate into two classes:**

1. **`UserApiDelegateImpl`** (always registered) — implements `getUserById` and `updateUserSettings` only. Annotated `@Service`.

2. **`NoAuthUserApiDelegateImpl`** (conditionally registered) — implements `registerUser`, `loginUser`, and `getActiveUserEmails` only. Annotated:
   ```kotlin
   @Service
   @ConditionalOnProperty(name = ["app.features.enable-no-auth"], havingValue = "true", matchIfMissing = false)
   class NoAuthUserApiDelegateImpl(private val userService: UserService) : UsersApiDelegate { ... }
   ```

> **Note:** Check whether the generated `UsersApiDelegate` interface has default method implementations for operations. If it does, the `NoAuthUserApiDelegateImpl` can safely inherit the defaults for `getUserById` / `updateUserSettings` and vice versa for `UserApiDelegateImpl`. If the generator does not produce defaults, both classes must implement all interface methods — have the unowned ones delegate to `super` or throw `UnsupportedOperationException`. Confirm by inspecting the generated `UsersApiDelegate` in `build/generated/`.

### Acceptance criteria

- [ ] `NoAuthUserApiDelegateImpl` carries `@ConditionalOnProperty(name = ["app.features.enable-no-auth"], havingValue = "true", matchIfMissing = false)`
- [ ] With `enable-no-auth=false`: Spring context does not contain a `NoAuthUserApiDelegateImpl` bean
- [ ] With `enable-no-auth=true`: `NoAuthUserApiDelegateImpl` bean is present; `registerUser`, `loginUser`, and `getActiveUserEmails` are reachable
- [ ] `getUserById` and `updateUserSettings` are reachable in both cases
- [ ] Unit test (KoTest + MockMvc): `POST /api/v1/users` returns 404 (not registered) when `enable-no-auth=false`
- [ ] Unit test (KoTest + MockMvc): `POST /api/v1/users/login` returns 404 when `enable-no-auth=false`
- [ ] Unit test (KoTest + MockMvc): `GET /api/v1/users/emails` returns 404 when `enable-no-auth=false`
- [ ] Unit test (KoTest + MockMvc): `POST /api/v1/users` returns 201 when `enable-no-auth=true`
- [ ] `./gradlew build` passes

### Depends on
SVC-1

---

## CONTROLLER-2 — Remove no-auth endpoint whitelist entries from SecurityConfig when flag is false

**Layer:** Controller / Config  
**Domain:** config  
**Flows:** `user-login`, `user-registration`

### What to change

`services/backend/src/main/kotlin/org/dpp/tradelab/config/SecurityConfig.kt`

Inject the `enable-no-auth` property and conditionally add the three permit-all matchers:

```kotlin
@Value("\${app.features.enable-no-auth:true}")
private val enableNoAuth: Boolean
```

In `securityFilterChain`, wrap the three no-auth matchers in a conditional block:

```kotlin
if (enableNoAuth) {
    auth.requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
    auth.requestMatchers(HttpMethod.POST, "/api/v1/users/login").permitAll()
    auth.requestMatchers(HttpMethod.GET, "/api/v1/users/emails").permitAll()
}
```

All other permit-all entries (OAuth2 dance, H2 console, actuator, admin, test) are unchanged.

### Acceptance criteria

- [ ] When `enable-no-auth=false`: `POST /api/v1/users`, `POST /api/v1/users/login`, and `GET /api/v1/users/emails` are not in the permit-all list
- [ ] When `enable-no-auth=true`: all three paths remain permit-all
- [ ] All other permit-all entries are unaffected in both cases
- [ ] Unit test: `SecurityConfig` bean loads without error for both `true` and `false` values of the property
- [ ] `./gradlew build` passes

### Depends on
SVC-1

---

## SCREEN-1 — Conditionally register `/login` and `/register` routes; add env files

**Layer:** Screen  
**Domain:** user  
**Flows:** `user-login`, `user-registration`

### What to change

**1. `services/front-end/src/app/router.tsx`**

Read the build-time flag:
```ts
const enableNoAuth = import.meta.env.VITE_ENABLE_NO_AUTH === 'true'
```

Conditionally build the route tree:
```ts
const noAuthRoutes = enableNoAuth ? [loginRoute, registerRoute] : []

const routeTree = rootRoute.addChildren([
  indexRoute,
  ...noAuthRoutes,
  authCallbackRoute,
  profileRoute,
  accountsRoute,
  transactionListRoute,
  tradeRoute,
  portfolioRoute,
])
```

Update `indexRoute` redirect based on the flag:
```ts
const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  beforeLoad: () => {
    throw redirect({ to: enableNoAuth ? '/login' : '/oauth2/authorization/google' })
  },
})
```

**2. `services/front-end/.env.development`**

Add:
```
VITE_ENABLE_NO_AUTH=true
```

**3. `services/front-end/.env.production`** (create new file)

```
VITE_ENABLE_NO_AUTH=false
```

### Acceptance criteria

- [ ] When `VITE_ENABLE_NO_AUTH=true`: `loginRoute` and `registerRoute` are in the route tree; `indexRoute` redirects to `/login`
- [ ] When `VITE_ENABLE_NO_AUTH=false` (or unset): `loginRoute` and `registerRoute` are absent; `indexRoute` redirects to `/oauth2/authorization/google`
- [ ] `.env.development` contains `VITE_ENABLE_NO_AUTH=true`
- [ ] `.env.production` exists and contains `VITE_ENABLE_NO_AUTH=false`
- [ ] Vitest test: with flag `true`, both route paths are present in the route children list
- [ ] Vitest test: with flag `false`, both route paths are absent
- [ ] `pnpm run build` passes

### Depends on
none
