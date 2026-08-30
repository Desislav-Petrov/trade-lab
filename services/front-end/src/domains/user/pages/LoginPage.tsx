import { useRouterState, useSearch } from '@tanstack/react-router'
import { LoginForm } from '../components/LoginForm'
import { LoginWithGoogleButton } from '../components/LoginWithGoogleButton'
import { LoginWithGithubButton } from '../components/LoginWithGithubButton'
import { redirectToGoogleLogin, redirectToGithubLogin } from '../api/oidcApi'
import { Card, CardContent, CardHeader } from '@/shared/components/ui/card'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'
import { Separator } from '@/shared/components/ui/separator'

interface LocationState {
  banner?: string
}

const OIDC_ERROR_MESSAGES: Record<string, string> = {
  google_oidc_failed: 'Google authentication failed. Please try again.',
  github_oidc_failed: 'GitHub authentication failed. Please try again.',
  github_no_email: 'Your GitHub account has no public email. Please make your email public on GitHub and try again.',
  server_error: 'Something went wrong. Please try again.',
}

export function LoginPage() {
  const routerState = useRouterState()
  const banner = (routerState.location.state as LocationState | null)?.banner ?? null

  const search = useSearch({ strict: false }) as Record<string, string | undefined>
  const errorCode = search?.error ?? null
  const oidcError = errorCode ? (OIDC_ERROR_MESSAGES[errorCode] ?? null) : null

  function handleGoogleLogin() {
    redirectToGoogleLogin()
  }

  function handleGithubLogin() {
    redirectToGithubLogin()
  }

  return (
    <main className="flex min-h-full items-center justify-center p-6">
      <Card className="w-full max-w-sm">
        <CardHeader className="pb-2">
          <p className="text-xs tracking-widest text-[var(--color-accent)]">TRADE-LAB</p>
          <h1 className="text-sm font-medium text-[var(--color-text-primary)]">Log in</h1>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {banner && (
            <Alert variant="success" role="status">
              <AlertDescription>{banner}</AlertDescription>
            </Alert>
          )}

          {oidcError && (
            <Alert variant="destructive" role="alert">
              <AlertDescription>{oidcError}</AlertDescription>
            </Alert>
          )}

          <div className="flex flex-col gap-2">
            <LoginWithGoogleButton onClick={handleGoogleLogin} />
            <LoginWithGithubButton onClick={handleGithubLogin} />
          </div>

          <div className="flex items-center gap-2">
            <Separator className="flex-1" />
            <span className="text-xs text-[var(--color-text-muted)]">or continue with email</span>
            <Separator className="flex-1" />
          </div>

          <LoginForm />

          <p className="text-xs text-[var(--color-text-muted)]">
            No account?{' '}
            <a href="/register" className="text-[var(--color-accent)] hover:underline">
              Register
            </a>
          </p>
        </CardContent>
      </Card>
    </main>
  )
}
