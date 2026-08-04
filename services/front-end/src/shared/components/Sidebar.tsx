import { Link } from '@tanstack/react-router'
import { useSessionStore } from '../../domains/user/hooks/useSessionStore'

const navLinkBase = 'rounded px-3 py-2 text-xs transition-colors'
const navLinkActive =
  'bg-[var(--color-surface-raised)] text-[var(--color-accent)]'
const navLinkInactive =
  'text-[var(--color-text-muted)] hover:bg-[var(--color-surface-raised)] hover:text-[var(--color-text-primary)]'

export function Sidebar() {
  const user = useSessionStore((s) => s.user)

  return (
    <aside className="flex h-full w-48 shrink-0 flex-col border-r border-[var(--color-border)] bg-[var(--color-surface)]">
      <nav aria-label="Main navigation" className="flex flex-col gap-0.5 p-2">
        {user && (
          <Link
            to="/profile"
            className={navLinkBase + ' ' + navLinkInactive}
            activeProps={{ className: navLinkBase + ' ' + navLinkActive }}
          >
            Profile
          </Link>
        )}
        {user && (
          <Link
            to="/accounts"
            className={navLinkBase + ' ' + navLinkInactive}
            activeProps={{ className: navLinkBase + ' ' + navLinkActive }}
          >
            Accounts
          </Link>
        )}
        {user && (
          <Link
            to="/trade"
            className={navLinkBase + ' ' + navLinkInactive}
            activeProps={{ className: navLinkBase + ' ' + navLinkActive }}
          >
            Stock Trading
          </Link>
        )}
        {user && (
          <Link
            to="/portfolio"
            className={navLinkBase + ' ' + navLinkInactive}
            activeProps={{ className: navLinkBase + ' ' + navLinkActive }}
          >
            Portfolio
          </Link>
        )}
      </nav>
    </aside>
  )
}
