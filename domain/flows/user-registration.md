# User Registration

## Overview

Allows a guest to create a new user account on the paper trading platform via the registration screen at `/register`. On successful completion, the user exists in the system with `active` status and is redirected to `/login` with a confirmation banner.

## Actors

- **Guest**: An unauthenticated visitor who wishes to register on the platform.
- **Guest Browser**: The React frontend rendering the registration form and handling user interaction.
- **System**: The platform backend responsible for validation and persistence.

## Preconditions

- The guest does not already have an account with the same email address.
- The registration screen is accessible at `/register` to unauthenticated visitors.

## Steps

| #  | Actor         | Action                    | Description                                                                                                                           |
|----|---------------|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| 1  | Guest Browser | Render registration form  | Displays input fields for `firstName`, `lastName`, `address`, and `email`, and a Submit button.                                       |
| 2  | Guest         | Fill in form fields       | Enters values for `firstName`, `lastName`, `address`, and `email`.                                                                    |
| 3  | Guest         | Click Submit              | Triggers form submission.                                                                                                             |
| 4  | Guest Browser | Show loading state        | Disables the Submit button and shows a loading indicator while the API request is in flight.                                          |
| 5  | System        | Validate required fields  | Checks that `firstName`, `lastName`, `address`, and `email` are all present and non-empty.                                            |
| 6  | System        | Validate email format     | Checks that `email` conforms to a valid email format.                                                                                 |
| 7  | System        | Check email uniqueness    | Queries existing users to confirm no account with the same `email` exists.                                                            |
| 8  | System        | Create user record        | Generates a new `id` (UUID), sets `status` to `active`, and persists the user with `createdAt` and `updatedAt` set to current timestamp. |
| 9  | System        | Emit event                | Emits `UserRegistered`.                                                                                                               |
| 10 | System        | Return HTTP 201           | Response body includes `userId`.                                                                                                      |
| 11 | Guest Browser | Navigate to `/login`      | Redirects the guest to `/login` and displays a success banner: "Account created. Please log in."                                      |

## Postconditions

- A `User` record exists in the system with `status` set to `active`.
- `UserRegistered` has been emitted.
- The guest is on the `/login` screen with a success banner displayed.
- The guest may now authenticate using their `email`.

## Error Cases

| Scenario               | Condition                                                                   | System Response | UI Outcome                                                                                                             |
|------------------------|-----------------------------------------------------------------------------|-----------------|------------------------------------------------------------------------------------------------------------------------|
| Missing required field | Any of `firstName`, `lastName`, `address`, or `email` is absent or empty    | HTTP 400        | Flow halts at step 5. Inline validation error displayed below each missing field. Submit button re-enabled.            |
| Invalid email format   | `email` does not conform to a valid email format                            | HTTP 400        | Flow halts at step 6. Inline validation error displayed below the email field. Submit button re-enabled.               |
| Duplicate email        | An existing user with the same `email` is found                             | HTTP 409        | Flow halts at step 7. Form-level error displayed at the top of the form: "An account with this email already exists." Submit button re-enabled. No user record is created. |

## Domain Models Involved

- **User**: Created at step 8 with all provided fields and a system-generated `id`.
