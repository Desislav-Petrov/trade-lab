# BE-02 — Backend: Add UserNotFoundException

## Layer
EXCEPTION (`services/backend/src/main/kotlin/org/dpp/tradelab/user/exception/`)

## Context
`GET /users/{userId}` needs to return HTTP 404 when no user exists for the given
ID. A typed domain exception is required so `GlobalExceptionHandler` can map it
correctly.

## Task
Create `UserNotFoundException.kt`:

```kotlin
package org.dpp.tradelab.user.exception

import java.util.UUID

class UserNotFoundException(userId: UUID) :
    RuntimeException("No user found with id: $userId")
```

Verify that `GlobalExceptionHandler` already handles `RuntimeException` with a
404. If it does not, add a handler:

```kotlin
@ExceptionHandler(UserNotFoundException::class)
fun handleUserNotFound(ex: UserNotFoundException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        ErrorResponse(status = 404, error = "User not found", details = listOf(ex.message ?: ""))
    )
```

## Acceptance Criteria
- `UserNotFoundException` exists in `user.exception`.
- `GlobalExceptionHandler` maps it to HTTP 404 with an `ErrorResponse` body.
- Unit test: `GlobalExceptionHandler` or delegate test asserts 404 is returned when `UserNotFoundException` is thrown.
