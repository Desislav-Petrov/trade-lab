# FE-06 — Frontend: Add conditional Profile link to Sidebar

## Layer
COMP (`services/front-end/src/shared/components/Sidebar.tsx`)

## Context
The Sidebar currently has three static nav items: Trade, Ledger, Market.
A Profile link must be added that is only visible when the user is logged in
(session store has a user).

## Prerequisite
FE-02 (session store) must be complete.

## Task
Update `Sidebar.tsx`:

1. Import `useSessionStore`.
2. Read `user` from the store.
3. Conditionally render a Profile `NavLink` below Market when `user !== null`.

```tsx
import { useSessionStore } from '../../domains/user/hooks/useSessionStore'

export function Sidebar() {
  const user = useSessionStore((s) => s.user)

  return (
    <aside className="flex h-full w-48 shrink-0 flex-col border-r border-[var(--color-border)] bg-[var(--color-surface)]">
      <nav aria-label="Main navigation" className="flex flex-col gap-0.5 p-2">
        {NAV_ITEMS.map(({ label, path }) => (
          <NavLink key={path} to={path} className={navLinkClass}>{label}</NavLink>
        ))}
        {user && (
          <NavLink to="/profile" className={navLinkClass}>
            Profile
          </NavLink>
        )}
      </nav>
    </aside>
  )
}
```

> Extract the `className` callback as a named `navLinkClass` function to avoid repetition.

## Tests (`Sidebar.test.tsx`)
- `Sidebar - no session - Profile link is not rendered`
- `Sidebar - session exists - Profile link is rendered`
- Existing tests must continue to pass.

## Acceptance Criteria
- Profile link absent when no session.
- Profile link present when session exists.
- All tests pass.
