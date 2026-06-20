# BE-01 — OpenAPI: Add GET /users/{userId} endpoint

## Layer
CONTRACT (`services/contract/user-openapi.yaml`)

## Context
The login flow now requires the frontend to fetch the full user profile after
obtaining a `userId` from the login response. A `GET /users/{userId}` endpoint
does not yet exist in the contract.

## Task
Add the following to `services/contract/user-openapi.yaml`:

### Path
```yaml
/users/{userId}:
  get:
    tags:
      - Users
    summary: Get a user by ID
    description: Returns the full profile of the user with the given ID.
    operationId: getUserById
    parameters:
      - name: userId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    responses:
      '200':
        description: User found
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UserResponse'
      '404':
        description: No user found for the given ID
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ErrorResponse'
```

### Schema
```yaml
UserResponse:
  type: object
  required:
    - userId
    - firstName
    - lastName
    - address
    - email
    - status
    - createdAt
  properties:
    userId:
      type: string
      format: uuid
    firstName:
      type: string
    lastName:
      type: string
    address:
      type: string
    email:
      type: string
      format: email
    status:
      type: string
      enum: [active, suspended, closed]
    createdAt:
      type: string
      format: date-time
```

## Acceptance Criteria
- `getUserById` operation defined in the OpenAPI contract.
- `UserResponse` schema defined with all fields.
- Existing paths and schemas are unchanged.
