import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { fetchUserById } from '../api/userApi'
import { useSessionStore } from '../hooks/useSessionStore'

function decodeJwtSub(token: string): string | null {
  try {
    const payload = token.split('.')[1]
    const decoded = JSON.parse(atob(payload))
    return typeof decoded.sub === 'string' ? decoded.sub : null
  } catch {
    return null
  }
}

export interface AuthCallbackHandlerProps {
  onSuccess: () => void
}

export function AuthCallbackHandler({ onSuccess }: AuthCallbackHandlerProps) {
  const [searchParams] = useSearchParams()
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const establishSession = useSessionStore((s) => s.establishSession)

  useEffect(() => {
    async function handleCallback() {
      const token = searchParams.get('token')

      if (!token) {
        setError('Authentication failed. Please try again.')
        setLoading(false)
        return
      }

      const userId = decodeJwtSub(token)
      if (!userId) {
        setError('Authentication failed. Please try again.')
        setLoading(false)
        return
      }

      try {
        const userResponse = await fetchUserById(userId)
        establishSession(userResponse, token)
        onSuccess()
      } catch {
        setError('Authentication failed. Please try again.')
        setLoading(false)
      }
    }

    handleCallback()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (loading && !error) {
    return (
      <div role="status" aria-label="Loading">
        <span className="text-xs text-[var(--color-text-muted)]">Completing sign in…</span>
      </div>
    )
  }

  if (error) {
    return (
      <p role="alert" className="text-xs text-[var(--color-danger)]">
        {error}
      </p>
    )
  }

  return null
}
