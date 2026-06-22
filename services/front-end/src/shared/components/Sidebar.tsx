import { NavLink } from 'react-router-dom'
import { useSessionStore } from '../../domains/user/hooks/useSessionStore'

function navLinkClass({ isActive }: { isActive: boolean }) {
  return [
    'rounded px-3 py-2 text-xs transition-colors',
    isActive
      ? 'bg-[var(--color-surface-raised)] text-[var(--color-accent)]'
      : 'text-[var(--color-text-muted)] hover:bg-[var(--color-surface-raised)] hover:text-[var(--color-text-primary)]',
  ].join(' ')
}

export function Sidebar() {
  const user = useSessionStore((s) => s.user)

  return (
    <aside className="flex h-full w-48 shrink-0 flex-col border-r border-[var(--color-border)] bg-[var(--color-surface)]">
      <nav aria-label="Main navigation" className="flex flex-col gap-0.5 p-2">
        {user && (
          <NavLink to="/profile" className={navLinkClass}>
            Profile
          </NavLink>
        )}
        {user && (
          <NavLink to="/accounts" className={navLinkClass}>
            Accounts
          </NavLink>
        )}
      </nav>
    </aside>
  )
}
