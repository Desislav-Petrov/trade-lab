# User

## Overview

Represents a registered user of the paper trading platform. Holds identity and contact information, and acts as the owner of one or more trading accounts.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| id | uuid | yes | Unique user identifier |
| firstName | string | yes | User's first name |
| lastName | string | yes | User's last name |
| address | string | yes | User's postal address |
| email | string | yes | User's email address, used for login and notifications |
| status | enum | yes | `active` \| `suspended` \| `closed` |
| createdAt | datetime | yes | Timestamp of account creation |
| updatedAt | datetime | yes | Timestamp of last profile update |

## Behaviors

- **Register**: Creates a new user with `active` status. Validates that `email` is unique across the platform.
- **Close**: Transitions status to `closed`. Irreversible; no further actions are permitted on the user.

## Relationships

- **Account** (`one-to-many`): A user may own one or more paper trading accounts.

## Business Rules

- `email` must be unique across all users.
- `email` must be a valid email format.
- `status` transitions are one-directional: `active` → `suspended` → `closed`. A closed user cannot be reopened.
- A suspended or closed user may not perform any trading actions.
- `firstName` and `lastName` must not be empty.
