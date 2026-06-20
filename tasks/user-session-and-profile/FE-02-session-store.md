# FE-02 — Frontend: Add Zustand session store

## Layer
STATE (`services/front-end/src/domains/user/hooks/useSessionStore.ts`)

## Context
The session holds the authenticated user's full profile in client memory.
Zustand is already installed. This store is the single source of truth for
authenticated state across the entire app.

## Prerequisite
FE-01 must be complete (needs `UserProfile` type).

## Task
Create `services/front-end/src/domains/user/hooks/useSessionStore.ts`:

```typescript
import { create } from 'zustand'
import type { UserProfile } from '../types/user'

interface SessionState {
  user: UserProfile | null
  loggedInAt: string | null  // ISO 8601 client-side timestamp
  setSession: (user: UserProfile) => void
  clearSession: () => void
}

export const useSessionStore = create<SessionState>((set) => ({
  user: null,
  loggedInAt: null,
  setSession: (user) =>
    set({ user, loggedInAt: new Date().toISOString() }),
  clearSession: () => set({ user: null, loggedInAt: null }),
}))
```

## Unit Tests (`useSessionStore.test.ts`)
- `useSessionStore - setSession - stores user and sets loggedInAt`
- `useSessionStore - clearSession - clears user and loggedInAt`
- `useSessionStore - initial state - user is null`

## Acceptance Criteria
- Store exported from `useSessionStore.ts`.
- `setSession`, `clearSession` work correctly.
- All three unit tests pass.
