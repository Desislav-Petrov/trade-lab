# FE-04 — Frontend: Update LoginPage to fetch profile after login

## Layer
SCREEN (`services/front-end/src/domains/user/pages/LoginPage.tsx`)

## Context
Currently `LoginPage.handleSuccess` navigates directly to `/trade` after login.
It now needs to first fetch the full user profile (via `useFetchUserProfile`)
and store it before navigating. If the profile fetch fails, the user stays on
`/login` with a generic error message.

## Prerequisite
FE-03 must be complete.

## Task
Update `LoginPage.tsx`:

1. Import `useFetchUserProfile`.
2. Instantiate the hook with `onSuccess` → navigate to `/trade`, `onError` → set a local error state.
3. Update `handleSuccess` to call `fetchProfileMutation.mutate(data.userId)` instead of navigating directly.
4. Render the error message if the profile fetch fails.

```tsx
const [profileError, setProfileError] = useState(false)
const fetchProfile = useFetchUserProfile({
  onSuccess: () => navigate('/trade'),
  onError: () => setProfileError(true),
})

function handleSuccess(data: LoginResponse) {
  fetchProfile.mutate(data.userId)
}
```

Render below the form when `profileError` is true:
```tsx
{profileError && (
  <p role="alert" className="mt-3 text-xs text-[var(--color-danger)]">
    Unable to load your profile. Please try again.
  </p>
)}
```

## Tests (`LoginPage.test.tsx`)
- `LoginPage - profile fetch succeeds - navigates to /trade`
- `LoginPage - profile fetch fails - shows profile error message`
- Existing tests must continue to pass.

## Acceptance Criteria
- Navigation to `/trade` only happens after profile fetch succeeds.
- Error message shown on profile fetch failure.
- All tests pass.
