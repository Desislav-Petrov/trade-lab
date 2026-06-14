# Use Case: Register a User

## Goal

A guest creates a new account on the paper trading platform via the registration screen so they can log in and start trading.

## Actor

Guest — an unauthenticated visitor accessing the platform for the first time.

## Screen

- **Route:** `/register`
- **Page:** `RegistrationPage`
- **Entry point:** Guest arrives from the `/login` screen via the "Register" link.

## Trigger

Guest clicks the Submit button on the registration form at `/register`.

## Domain Models

- `domain/model/user`

## Flows

- `domain/flows/user-registration`

## Happy Path

1. Guest navigates to `/register`.
2. Guest Browser renders the registration form displaying input fields for `firstName`, `lastName`, `address`, and `email`, and a Submit button.
3. Guest fills in all fields and clicks Submit.
4. Guest Browser disables the Submit button and shows a loading indicator.
5. System validates all fields and confirms the email is not already in use.
6. System creates the `User` record with `status: active` and returns HTTP 201.
7. Guest Browser redirects to `/login` and displays a success banner: "Account created. Please log in."

## Failure Scenarios

| Scenario                | Trigger                                         | UI Outcome                                                                                                              |
|-------------------------|-------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| Missing required field  | Any field absent or empty                       | Inline validation error below each missing field. Submit button re-enabled.                                             |
| Invalid email format    | `email` fails format check                      | Inline validation error below the email field. Submit button re-enabled.                                                |
| Email already registered | Server returns HTTP 409                        | Form-level error at the top of the form: "An account with this email already exists." Submit button re-enabled.         |

## Out of Scope

- Password creation or credential setup (no authentication in this iteration).
- Email verification.
- Automatic login after registration.
- Creating a trading account as part of registration.
