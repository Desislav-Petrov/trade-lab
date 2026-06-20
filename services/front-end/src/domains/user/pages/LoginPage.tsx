import { useNavigate, useLocation } from 'react-router-dom'
import { LoginForm } from '../components/LoginForm'
import type { LoginResponse } from '../types/user'

interface LocationState {
  banner?: string
}

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const banner = (location.state as LocationState | null)?.banner ?? null

  function handleSuccess(_data: LoginResponse) {
    navigate('/trade')
  }

  return (
    <main className="flex min-h-full items-center justify-center p-6">
      <div className="w-full max-w-sm rounded border border-[var(--color-border)] bg-[var(--color-surface)] p-8">
        <p className="mb-1 text-xs tracking-widest text-[var(--color-accent)]">TRADE-LAB</p>
        <h1 className="mb-6 text-sm font-medium text-[var(--color-text-primary)]">Log in</h1>

        {banner && (
          <p
            role="status"
            className="mb-4 border-l-2 border-[var(--color-accent)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-accent)]"
          >
            {banner}
          </p>
        )}

        <LoginForm onSuccess={handleSuccess} />

        <p className="mt-6 text-xs text-[var(--color-text-muted)]">
          No account?{' '}
          <a href="/register" className="text-[var(--color-accent)] hover:underline">
            Register
          </a>
        </p>
      </div>
    </main>
  )
}
