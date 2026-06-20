import { NavLink } from 'react-router-dom'

interface NavItem {
  label: string
  path: string
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Trade', path: '/trade' },
  { label: 'Ledger', path: '/ledger' },
  { label: 'Market', path: '/market' },
]

export function Sidebar() {
  return (
    <aside className="flex h-full w-48 shrink-0 flex-col border-r border-[var(--color-border)] bg-[var(--color-surface)]">
      <nav aria-label="Main navigation" className="flex flex-col gap-0.5 p-2">
        {NAV_ITEMS.map(({ label, path }) => (
          <NavLink
            key={path}
            to={path}
            className={({ isActive }) =>
              [
                'rounded px-3 py-2 text-xs transition-colors',
                isActive
                  ? 'bg-[var(--color-surface-raised)] text-[var(--color-accent)]'
                  : 'text-[var(--color-text-muted)] hover:bg-[var(--color-surface-raised)] hover:text-[var(--color-text-primary)]',
              ].join(' ')
            }
          >
            {label}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}
