# Tasks: ENABLE_NO_AUTH — Gate Email Login and Registration (Issue #176)

## Context

The email-select login (`domain/flows/user-login`) and manual email registration (`domain/flows/user-registration`) flows are local-testing conveniences only. Neither performs any credential check. This task set gates both flows behind an `ENABLE_NO_AUTH` environment variable so they cannot be reached in production.

**Decision log:** `decisions/2026-08-30-enable-no-auth-flag.md`  
**Flows updated:** `domain/flows/user-login.md`, `domain/flows/user-registration.md`

---

## Approach

The no-auth operations (`POST /api/v1/users`, `POST /api/v1/users/login`, `GET /api/v1/users/emails`) are moved into a dedicated OpenAPI contract file (`user-noauth-openapi.yaml`). This generates its own `UsersNoAuthApiDelegate` interface and `UsersNoAuthApiController`. A single `UserNoAuthApiDelegateImpl` implements that interface and is gated with `@ConditionalOnProperty`. The main `user-openapi.yaml` and `UserApiDelegateImpl` are untouched beyond removing the three no-auth paths.

---

## Dependency order

```
CONTRACT-1
    │
    ├──► SVC-1
    │       │
    │       ├──► CONTROLLER-1
    │       └──► CONTROLLER-2
    └──► BUILD-1 (depends on CONTRACT-1 only)
```

---

## CONTRACT-1 — Split user OpenAPI into core and no-auth specs

**Layer:** API Contract  
**Domain:** user  
**Flows:** `user-login`, `user-registration`

### What to change

**1. `services/contract/user-openapi.yaml`**

Remove the following three paths entirely:
- `POST /users` (registerUser)
- `POST /users/login` (loginUser)
- `GET /users/emails` (getActiveUserEmails)

Also remove the schemas that are exclusively used by those paths and no longer referenced by the remaining paths:
- `RegisterUserRequest`
- `RegisterUserResponse`
- `UserEmailsResponse`
- `LoginRequest`
- `LoginTokenResponse`

The remaining paths after removal: `GET /users/{userId}`, `PATCH /users/{userId}/settings`.  
The remaining schemas: `UserResponse`, `UserSettingsResponse`, `UpdateUserSettingsRequest`, `ErrorResponse`.

**2. `services/contract/user-noauth-openapi.yaml`** (create new file)

Create a new OpenAPI 3.0.3 contract containing only the three no-auth paths:
- `POST /users` — `registerUser` — request: `RegisterUserRequest`, response: `RegisterUserResponse`
- `POST /users/login` — `loginUser` — request: `LoginRequest`, response: `LoginTokenResponse`
- `GET /users/emails` — `getActiveUserEmails` — response: `UserEmailsResponse`

Use the same schema definitions as currently exist in `user-openapi.yaml` for these paths:
- `info.title`: `Trade Lab API — User No-Auth`
- `apiPackage`: `org.dpp.tradelab.user.generated.noauth.api`
- `modelPackage`: `org.dpp.tradelab.user.generated.noauth.model`

Carry over all relevant schemas (`RegisterUserRequest`, `RegisterUserResponse`, `LoginRequest`, `LoginTokenResponse`, `UserEmailsResponse`, `ErrorResponse`) into the `components/schemas` section of the new file.

### Acceptance criteria

- [ ] `user-openapi.yaml` no longer contains `POST /users`, `POST /users/login`, or `GET /users/emails`
- [ ] `user-openapi.yaml` no longer contains `RegisterUserRequest`, `RegisterUserResponse`, `UserEmailsResponse`, `LoginRequest`, `LoginTokenResponse` schemas
- [ ] `user-noauth-openapi.yaml` exists and contains all three no-auth paths with their full request/response schemas
- [ ] `user-noauth-openapi.yaml` passes OpenAPI 3.0.3 schema validation (run `./gradlew generateUserNoAuthApi` to verify generation succeeds)

### Depends on
none

---

## BUILD-1 — Add `generateUserNoAuthApi` Gradle task and wire sources

**Layer:** Build  
**Domain:** user  
**Flows:** `user-login`, `user-registration`

### What to change

`services/backend/build.gradle.kts`

**1.** Register a new `GenerateTask` for the no-auth spec:

```kotlin
val generateUserNoAuthApi = tasks.register<GenerateTask>("generateUserNoAuthApi") {
    generatorName.set("kotlin-spring")
    inputSpec.set("${rootProject.projectDir}/../../services/contract/user-noauth-openapi.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/user-noauth")
    apiPackage.set("org.dpp.tradelab.user.generated.noauth.api")
    modelPackage.set("org.dpp.tradelab.user.generated.noauth.model")
    configOptions.set(mapOf(
        "useSpringBoot3" to "true",
        "delegatePattern" to "true",
        "serializationLibrary" to "jackson",
        "enumPropertyNaming" to "UPPERCASE",
        "gradleBuildFile" to "false",
        "exceptionHandler" to "false"
    ))
}
```

**2.** Add the generated source directory to the main source set:

```kotlin
srcDir("${layout.buildDirectory.get()}/generated/user-noauth/src/main/kotlin")
```

**3.** Wire `generateUserNoAuthApi` into `compileKotlin`:

```kotlin
tasks.named("compileKotlin") {
    dependsOn(generateUserApi, generateUserNoAuthApi, generateLedgerApi, ...)
}
```

### Acceptance criteria

- [ ] `./gradlew generateUserNoAuthApi` succeeds and produces sources under `build/generated/user-noauth/`
- [ ] `./gradlew compileKotlin` succeeds with both `generateUserApi` and `generateUserNoAuthApi` as dependencies
- [ ] Generated `UsersNoAuthApiDelegate` and `UsersNoAuthApiController` are on the compile classpath

### Depends on
CONTRACT-1

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

## CONTROLLER-1 — Implement `UserNoAuthApiDelegateImpl` with `@ConditionalOnProperty`

**Layer:** Controller  
**Domain:** user  
**Flows:** `user-login`, `user-registration`

### What to change

**1. Create** `services/backend/src/main/kotlin/org/dpp/tradelab/user/controller/UserNoAuthApiDelegateImpl.kt`

This class implements the generated `UsersNoAuthApiDelegate` (from `user-noauth-openapi.yaml`) and is only registered when `app.features.enable-no-auth=true`:

```kotlin
@Service
@ConditionalOnProperty(name = ["app.features.enable-no-auth"], havingValue = "true", matchIfMissing = false)
class UserNoAuthApiDelegateImpl(
    private val userService: UserService
) : UsersNoAuthApiDelegate {

    override fun registerUser(registerUserRequest: RegisterUserRequest): ResponseEntity<RegisterUserResponse> { ... }
    override fun getActiveUserEmails(): ResponseEntity<UserEmailsResponse> { ... }
    override fun loginUser(loginRequest: LoginRequest): ResponseEntity<LoginTokenResponse> { ... }
}
```

Move the implementations of `registerUser`, `getActiveUserEmails`, and `loginUser` from the existing `UserApiDelegateImpl` into this new class. The import types come from `org.dpp.tradelab.user.generated.noauth.model` and `org.dpp.tradelab.user.generated.noauth.api`.

**2. Update** `services/backend/src/main/kotlin/org/dpp/tradelab/user/controller/UserApiDelegateImpl.kt`

Remove `registerUser`, `getActiveUserEmails`, and `loginUser` — they now live in `UserNoAuthApiDelegateImpl`. Remove the imports for the models that are no longer referenced (`RegisterUserRequest`, `RegisterUserResponse`, `UserEmailsResponse`, `LoginRequest`, `LoginTokenResponse`). The class now implements only `getUserById` and `updateUserSettings`.

### Acceptance criteria

- [ ] `UserNoAuthApiDelegateImpl` exists at `user/controller/` and implements `UsersNoAuthApiDelegate`
- [ ] `UserNoAuthApiDelegateImpl` is annotated `@ConditionalOnProperty(name = ["app.features.enable-no-auth"], havingValue = "true", matchIfMissing = false)`
- [ ] `UserApiDelegateImpl` no longer contains `registerUser`, `getActiveUserEmails`, or `loginUser`
- [ ] With `enable-no-auth=false`: Spring context does not contain a `UserNoAuthApiDelegateImpl` bean
- [ ] With `enable-no-auth=true`: all three no-auth endpoints are reachable
- [ ] `getUserById` and `updateUserSettings` remain reachable in both cases
- [ ] Unit test (KoTest + MockMvc): `POST /api/v1/users` returns 404 when `enable-no-auth=false`
- [ ] Unit test (KoTest + MockMvc): `POST /api/v1/users/login` returns 404 when `enable-no-auth=false`
- [ ] Unit test (KoTest + MockMvc): `GET /api/v1/users/emails` returns 404 when `enable-no-auth=false`
- [ ] Unit test (KoTest + MockMvc): `POST /api/v1/users` returns 201 when `enable-no-auth=true`
- [ ] `./gradlew build` passes

### Depends on
BUILD-1, SVC-1

---

## CONTROLLER-2 — Gate no-auth permit-all entries in SecurityConfig

**Layer:** Controller / Config  
**Domain:** config  
**Flows:** `user-login`, `user-registration`

### What to change

`services/backend/src/main/kotlin/org/dpp/tradelab/config/SecurityConfig.kt`

Inject the property and conditionally add the three permit-all matchers:

```kotlin
@Value("\${app.features.enable-no-auth:true}")
private val enableNoAuth: Boolean
```

```kotlin
if (enableNoAuth) {
    auth.requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
    auth.requestMatchers(HttpMethod.POST, "/api/v1/users/login").permitAll()
    auth.requestMatchers(HttpMethod.GET, "/api/v1/users/emails").permitAll()
}
```

All other permit-all entries (OAuth2 dance, H2 console, actuator, admin, test-mode) are unchanged.

### Acceptance criteria

- [ ] When `enable-no-auth=false`: the three no-auth paths are not in the permit-all list
- [ ] When `enable-no-auth=true`: all three paths remain permit-all
- [ ] All other permit-all entries are unaffected in both cases
- [ ] Unit test: `SecurityConfig` bean loads without error for both property values
- [ ] `./gradlew build` passes

### Depends on
SVC-1
