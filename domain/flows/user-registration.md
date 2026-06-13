# User Registration

## Overview

Allows a guest to create a new user account on the paper trading platform. On successful completion, the user exists in the system with `active` status and is able to log in and open trading accounts.

## Actors

- **Guest**: An unauthenticated visitor who wishes to register on the platform.
- **System**: The platform backend responsible for validation and persistence.

## Preconditions

- The guest does not already have an account with the same email address.

## Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest | Submit registration form | Provides `firstName`, `lastName`, `address`, and `email`. |
| 2 | System | Validate required fields | Checks that `firstName`, `lastName`, `address`, and `email` are all present and non-empty. |
| 3 | System | Validate email format | Checks that `email` conforms to a valid email format. |
| 4 | System | Check email uniqueness | Queries existing users to confirm no account with the same `email` exists. |
| 5 | System | Create user record | Generates a new `id` (uuid), sets `status` to `active`, and persists the user with `createdAt` and `updatedAt` set to the current timestamp. |
| 6 | System | Emit event | Emits `UserRegistered`. |
| 7 | System | Return confirmation | Responds to the guest with the new `userId` and confirmation of successful registration. |

## Postconditions

- A `User` record exists in the system with `status` set to `active`.
- `UserRegistered` has been emitted.
- The guest may now authenticate using their `email`.


## Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|---------|
| Missing required field | Any of `firstName`, `lastName`, `address`, or `email` is absent or empty | Flow halts at step 2; system returns a validation error identifying the missing field. |
| Invalid email format | `email` does not conform to a valid email format | Flow halts at step 3; system returns a validation error. |
| Duplicate email | An existing user with the same `email` is found | Flow halts at step 4; system returns a conflict error. No user record is created. |

## Domain Models Involved

- **User**: Created at step 5 with all provided fields and a system-generated `id`.
