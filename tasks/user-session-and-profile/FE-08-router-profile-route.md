# FE-08 — Frontend: Add /profile route to router

## Layer
SCREEN (`services/front-end/src/app/router.tsx`)

## Context
The `ProfilePage` exists but is not registered in the router. This task wires
it in.

## Prerequisite
FE-07 must be complete.

## Task
Update `services/front-end/src/app/router.tsx`:

1. Import `ProfilePage` from `domains/user/pages/ProfilePage`.
2. Add `{ path: '/profile', element: <ProfilePage /> }` as a child of the root layout route.

```tsx
import { ProfilePage } from '../domains/user/pages/ProfilePage'

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      { index: true, element: <Navigate to="/login" replace /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegistrationPage /> },
      { path: '/profile', element: <ProfilePage /> },
    ],
  },
])
```

## Acceptance Criteria
- Navigating to `/profile` renders `ProfilePage`.
- Unauthenticated navigation to `/profile` redirects to `/login` (enforced by `ProfilePage` itself).
- No existing routes are affected.
