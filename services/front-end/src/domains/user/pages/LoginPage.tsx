import { useState } from 'react'
import { useNavigate, useRouterState, useSearch } from '@tanstack/react-router'
import { LoginForm } from '../components/LoginForm'
import { useFetchUserProfile } from '../hooks/useFetchUserProfile'
import { LoginWithGoogleButton } from '../components/LoginWithGoogleButton'
import { redirectToGoogleLogin } from '../api/oidcApi'
import type { LoginResponse } from '../types/user'

interface LocationState {
  banner?: string
}

const OIDC_ERROR_MESSAGES: Record<string, string> = {
  oidc_failed: 'Authentication failed. Please try again.',
  server_error: 'Something went wrong. Please try again.',
}

export function LoginPage() {
  const navigate = useNavigate()
  const routerState = useRouterState()
  const banner = (routerState.location.state as LocationState | null)?.banner ?? null
  const [profileError, setProfileError] = useState(false)

  const search = useSearch({ strict: false }) as Record<string, string | undefined>
  const errorCode = search?.error ?? null
  const oidcError = errorCode ? (OIDC_ERROR_MESSAGES[errorCode] ?? null) : null

  const fetchProfile = useFetchUserProfile({
    onSuccess: () => navigate({ to: '/profile' }),
    onError: () => setProfileError(true),
  })

  function handleSuccess(data: LoginResponse) {
    setProfileError(false)
    fetchProfile.mutate(data.userId)
  }

  function handleGoogleLogin() {
    redirectToGoogleLogin()
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

        {oidcError && (
          <p
            role="alert"
            className="mb-4 border-l-2 border-[var(--color-danger)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-danger)]"
          >
            {oidcError}
          </p>
        )}

        <div className="mb-6">
          <LoginWithGoogleButton onClick={handleGoogleLogin} />
        </div>

        <div className="mb-4 flex items-center gap-2">
          <hr className="flex-1 border-[var(--color-border)]" />
          <span className="text-xs text-[var(--color-text-muted)]">or continue with email</span>
          <hr className="flex-1 border-[var(--color-border)]" />
        </div>

        <LoginForm onSuccess={handleSuccess} />

        {profileError && (
          <p role="alert" className="mt-3 text-xs text-[var(--color-danger)]">
            Unable to load your profile. Please try again.
          </p>
        )}

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
