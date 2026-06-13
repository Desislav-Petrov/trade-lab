# Use Case: Register a User

## Goal

A guest creates a new account on the paper trading platform so they can log in and start trading.

## Actor

Guest — an unauthenticated visitor accessing the platform for the first time.

## Trigger

Guest submits the registration form with their personal details.

## Domain Models

- `models/user`

## Flows

- `flows/user-registration`

## Happy Path

1. Guest navigates to the registration screen.
2. Guest enters `firstName`, `lastName`, `address`, and `email`.
3. Guest submits the form.
4. System validates all fields and confirms the email is not already in use.
5. System creates a new `User` record with `active` status.
6. Guest sees a confirmation that their account has been created and may now log in.

## Failure Scenarios

| Scenario | Outcome |
|---|---|
| Missing required field | System highlights the missing field and blocks submission. |
| Invalid email format | System displays a validation error and blocks submission. |
| Email already registered | System returns a conflict error; no new user is created. |

## Out of Scope

- Password creation or credential setup (no authentication in this iteration).
- Email verification.
- Automatic login after registration.
- Creating a trading account as part of registration.
