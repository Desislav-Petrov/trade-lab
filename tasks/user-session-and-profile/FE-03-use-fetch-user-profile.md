# FE-03 — Frontend: Add useFetchUserProfile hook

## Layer
STATE (`services/front-end/src/domains/user/hooks/useFetchUserProfile.ts`)

## Context
After login succeeds and returns a `userId`, the frontend must fetch the full
profile and store it in the session store. This hook encapsulates that logic.

## Prerequisite
FE-01 (API function), FE-02 (session store) must be complete.

## Task
Create `services/front-end/src/domains/user/hooks/useFetchUserProfile.ts`:

```typescript
import { useMutation } from '@tanstack/react-query'
import { fetchUserById } from '../api/userApi'
import { useSessionStore } from './useSessionStore'

interface UseFetchUserProfileOptions {
  onSuccess?: () => void
  onError?: () => void
}

export function useFetchUserProfile({ onSuccess, onError }: UseFetchUserProfileOptions = {}) {
  const setSession = useSessionStore((s) => s.setSession)

  return useMutation({
    mutationFn: (userId: string) => fetchUserById(userId),
    onSuccess: (profile) => {
      setSession(profile)
      onSuccess?.()
    },
    onError: () => {
      onError?.()
    },
  })
}
```

## Unit Tests (`useFetchUserProfile.test.ts`)
- `useFetchUserProfile - success - calls setSession with profile data`
- `useFetchUserProfile - error - does not call setSession, calls onError`

## Acceptance Criteria
- Hook exported from `useFetchUserProfile.ts`.
- On success: `setSession` is called with the fetched profile.
- On error: `setSession` is NOT called.
- Both unit tests pass.
