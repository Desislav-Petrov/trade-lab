import { useNavigate } from 'react-router-dom'
import { useSessionStore } from '../../domains/user/hooks/useSessionStore'

export function Topbar() {
  const user = useSessionStore((s) => s.user)
  const clearSession = useSessionStore((s) => s.clearSession)
  const navigate = useNavigate()

  function handleLogout() {
    clearSession()
    navigate('/login')
  }

  const today = new Date().toLocaleDateString(undefined, {
    weekday: 'short',
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })

  return (
    <header
      aria-label="Top bar"
      className="flex h-12 shrink-0 items-center justify-between border-b border-[var(--color-border)] bg-[var(--color-surface)] px-4"
    >
      <span className="text-xs font-medium tracking-widest text-[var(--color-accent)]">
        TRADE-LAB
      </span>
      <div
        aria-label="User area"
        className="flex items-center gap-4 text-xs text-[var(--color-text-muted)]"
      >
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
