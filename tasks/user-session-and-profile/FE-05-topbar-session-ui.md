# FE-05 — Frontend: Update Topbar with session-aware user area

## Layer
COMP (`services/front-end/src/shared/components/Topbar.tsx`)

## Context
The topbar currently renders a static `—` in the user area. It must now:
- When no session: render "Login or Register" text linking to `/login`.
- When session exists: render "Logged in as [firstName] [lastName]", today's date, and a Logout button.

## Prerequisite
FE-02 (session store) must be complete.

## Task
Update `Topbar.tsx`:

```tsx
import { useSessionStore } from '../../domains/user/hooks/useSessionStore'
import { useNavigate } from 'react-router-dom'

export function Topbar() {
  const user = useSessionStore((s) => s.user)
  const clearSession = useSessionStore((s) => s.clearSession)
  const navigate = useNavigate()

  function handleLogout() {
    clearSession()
    navigate('/login')
  }

  const today = new Date().toLocaleDateString(undefined, {
    weekday: 'short', year: 'numeric', month: 'short', day: 'numeric',
  })

  return (
    <header
      aria-label="Top bar"
      className="flex h-12 shrink-0 items-center justify-between border-b border-[var(--color-border)] bg-[var(--color-surface)] px-4"
    >
      <span className="text-xs font-medium tracking-widest text-[var(--color-accent)]">
        TRADE-LAB
      </span>
      <div aria-label="User area" className="flex items-center gap-4 text-xs text-[var(--color-text-muted)]">
        {user ? (
          <>
            <span>{today}</span>
            <span className="text-[var(--color-text-primary)]">
              Logged in as {user.firstName} {user.lastName}
            </span>
            <button
              onClick={handleLogout}
              className="rounded border border-[var(--color-border)] px-2 py-1 text-xs text-[var(--color-text-muted)] hover:border-[var(--color-accent)] hover:text-[var(--color-accent)] transition-colors"
            >
              Logout
            </button>
          </>
        ) : (
          <a href="/login" className="hover:text-[var(--color-text-primary)] transition-colors">
            Login or Register
          </a>
        )}
      </div>
    </header>
  )
}
```

## Tests (`Topbar.test.tsx`)
- `Topbar - no session - shows Login or Register`
- `Topbar - session exists - shows logged in as name and date`
- `Topbar - logout button clicked - clears session and navigates to /login`
- Existing tests must continue to pass.

## Acceptance Criteria
- Unauthenticated state shows "Login or Register" link.
- Authenticated state shows name, date, and Logout button.
- Logout clears Zustand store and redirects to `/login`.
- All tests pass.
