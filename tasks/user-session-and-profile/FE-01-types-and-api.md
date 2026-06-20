# FE-01 — Frontend: Add UserProfile types and fetchUserById API call

## Layer
CLI (`services/front-end/src/domains/user/api/userApi.ts`) +
TYPES (`services/front-end/src/domains/user/types/user.ts`)

## Context
After login the frontend calls `GET /api/v1/users/{userId}` to fetch the full
user profile. The types and API function do not exist yet.

## Prerequisite
BE-01 (OpenAPI contract) must be complete so the endpoint contract is known.

## Task

### 1. Add to `types/user.ts`
```typescript
export type UserStatus = 'active' | 'suspended' | 'closed'

export interface UserProfile {
  userId: string
  firstName: string
  lastName: string
  address: string
  email: string
  status: UserStatus
  createdAt: string  // UTC ISO 8601 — convert to local only at display layer
}
```

### 2. Add to `userApi.ts`
```typescript
export const GET_USER_KEY = (userId: string) => ['users', userId] as const

export async function fetchUserById(userId: string): Promise<UserProfile> {
  const response = await axiosInstance.get<UserProfile>(`/v1/users/${userId}`)
  return response.data
}
```

## Unit Tests (`userApi.test.ts`)
- `fetchUserById - success - returns UserProfile` — mock axios, assert correct URL called and data returned.
- `fetchUserById - 404 - throws AxiosError` — mock axios to reject with 404, assert error propagates.

## Acceptance Criteria
- `UserProfile` type exported from `types/user.ts`.
- `fetchUserById` function exported from `userApi.ts`.
- `GET_USER_KEY` constant exported.
- Both unit tests pass.
