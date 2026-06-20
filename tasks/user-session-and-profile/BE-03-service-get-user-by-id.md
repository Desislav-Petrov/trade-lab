# BE-03 — Backend: Add getUserById to UserService

## Layer
SVC (`services/backend/src/main/kotlin/org/dpp/tradelab/user/service/UserService.kt`)

## Context
The controller needs to delegate to the service to retrieve a user by UUID.
The repository already extends `JpaRepository<User, UUID>` so `findById` is
available without changes.

## Task
Add the following method to `UserService`:

```kotlin
@Transactional(readOnly = true)
fun getUserById(userId: UUID): User =
    userRepository.findById(userId)
        .orElseThrow { UserNotFoundException(userId) }
```

## Unit Tests (KoTest + mockito-kotlin)
Add to `UserServiceTest`:

- `getUserById_existingId_returnsUser` — mock `findById` returns `Optional.of(user)`, assert returned user equals mock.
- `getUserById_unknownId_throwsUserNotFoundException` — mock `findById` returns `Optional.empty()`, assert `UserNotFoundException` is thrown.

## Acceptance Criteria
- Method exists on `UserService`.
- `@Transactional(readOnly = true)` applied.
- Both unit tests pass.
