# BE-04 — Backend: Implement getUserById in UserApiDelegateImpl

## Layer
CONTROLLER (`services/backend/src/main/kotlin/org/dpp/tradelab/user/controller/UserApiDelegateImpl.kt`)

## Context
After completing BE-01 (OpenAPI contract) and running `./gradlew openApiGenerate`,
the generated `UsersApiDelegate` will contain a `getUserById` method. This task
implements that method in `UserApiDelegateImpl`.

## Prerequisite
BE-01, BE-02, BE-03 must be complete.

## Task
Add the following override to `UserApiDelegateImpl`:

```kotlin
override fun getUserById(userId: UUID): ResponseEntity<UserResponse> {
    val user = userService.getUserById(userId)
    return ResponseEntity.ok(
        UserResponse(
            userId = user.id!!,
            firstName = user.firstName,
            lastName = user.lastName,
            address = user.address,
            email = user.email,
            status = UserResponse.Status.valueOf(user.status.name.lowercase()
                .replaceFirstChar { it.uppercase() }),
            createdAt = user.createdAt!!
        )
    )
}
```

> Note: The exact enum mapping depends on the generated `UserResponse.Status` enum values.
> Adjust to match what the generator produces from the OpenAPI enum `[active, suspended, closed]`.

## MockMvc Tests (SpringBootTest)
Add to `UserApiDelegateImplTest`:

- `getUserById_existingUser_returns200WithUserResponse` — mock `userService.getUserById` returns a user, assert 200 and all response fields.
- `getUserById_unknownId_returns404` — mock `userService.getUserById` throws `UserNotFoundException`, assert 404 and error body.

## Acceptance Criteria
- `GET /api/v1/users/{userId}` returns 200 with full `UserResponse`.
- `GET /api/v1/users/{userId}` with unknown ID returns 404.
- Both MockMvc tests pass.
- `./gradlew test` passes.
