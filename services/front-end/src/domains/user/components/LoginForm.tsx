import type { ReactElement } from 'react'
import type { AxiosError } from 'axios'
import { useActiveUserEmails } from '../hooks/useActiveUserEmails'
import { useLoginUser } from '../hooks/useLoginUser'
import { Button } from '@/shared/components/ui/button'
import { Label } from '@/shared/components/ui/label'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'
import { Skeleton } from '@/shared/components/ui/skeleton'

export function LoginForm(): ReactElement {
  const { data, isLoading, isError } = useActiveUserEmails()
  const { mutate, isPending, error } = useLoginUser()

  const emails = data?.emails ?? []
  const hasNoUsers = !isLoading && !isError && emails.length === 0

  const serverError = error
    ? (error as AxiosError)?.response?.status === 404
      ? 'No account found for this email address.'
      : (error as AxiosError)?.response?.status === 403
        ? 'This account is suspended or closed.'
        : 'Something went wrong. Please try again.'
    : null

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = e.currentTarget
    const email = (form.elements.namedItem('email') as HTMLSelectElement).value
    if (email) mutate({ email })
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      {serverError && (
        <Alert variant="destructive" role="alert">
          <AlertDescription>{serverError}</AlertDescription>
        </Alert>
      )}

      {isLoading && (
        <div className="flex flex-col gap-2" data-testid="loading-indicator">
          <span className="sr-only">Loading accounts</span>
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-8 w-full" />
        </div>
      )}

      {isError && (
        <Alert variant="destructive" role="alert">
          <AlertDescription>Failed to load accounts. Please refresh.</AlertDescription>
        </Alert>
      )}

      {hasNoUsers && (
        <p className="text-xs text-[var(--color-text-muted)]">
          No active accounts found. Please register first.
        </p>
      )}

      {!isLoading && !isError && emails.length > 0 && (
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="email">Select account</Label>
          <select
            id="email"
            name="email"
            defaultValue=""
            className="flex h-8 w-full rounded border border-[hsl(var(--border))] bg-transparent px-3 py-1 text-xs text-[hsl(var(--foreground))] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[hsl(var(--ring))]"
          >
            <option value="" disabled>
              — choose an email —
            </option>
            {emails.map((email) => (
              <option key={email} value={email}>
                {email}
              </option>
            ))}
          </select>
        </div>
      )}

      <Button
        type="submit"
        className="mt-2 w-full"
        disabled={isPending || isLoading || emails.length === 0}
      >
        {isPending ? 'Logging in…' : 'Log in'}
      </Button>
    </form>
  )
}
