import type { AxiosError } from 'axios'
import { useActiveUserEmails } from '../hooks/useActiveUserEmails'
import { useLoginUser } from '../hooks/useLoginUser'
import type { LoginResponse } from '../types/user'

interface LoginFormProps {
  onSuccess?: (data: LoginResponse) => void
}

export function LoginForm({ onSuccess }: LoginFormProps) {
  const { data, isLoading, isError } = useActiveUserEmails()
  const { mutate, isPending, error } = useLoginUser({ onSuccess })

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
        <p
          role="alert"
          className="border-l-2 border-[var(--color-danger)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-danger)]"
        >
          {serverError}
        </p>
      )}

      {isLoading && (
        <p className="text-xs text-[var(--color-text-muted)]">Loading accounts…</p>
      )}

      {isError && (
        <p role="alert" className="text-xs text-[var(--color-danger)]">
          Failed to load accounts. Please refresh.
        </p>
      )}

      {hasNoUsers && (
        <p className="text-xs text-[var(--color-text-muted)]">
          No active accounts found. Please register first.
        </p>
      )}

      {!isLoading && !isError && emails.length > 0 && (
        <div className="flex flex-col gap-1">
          <label
            htmlFor="email"
            className="text-xs uppercase tracking-wider text-[var(--color-text-muted)]"
          >
            Select account
          </label>
          <select
            id="email"
            name="email"
            defaultValue=""
            className="w-full rounded border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-text-primary)] focus:border-[var(--color-accent)] focus:outline-none"
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

      <button
        type="submit"
        disabled={isPending || isLoading || emails.length === 0}
        className="mt-2 w-full rounded bg-[var(--color-accent)] px-4 py-2 text-xs font-medium text-[var(--color-bg)] transition-opacity disabled:cursor-not-allowed disabled:opacity-40"
      >
        {isPending ? 'Logging in…' : 'Log in'}
      </button>
    </form>
  )
}
