import { useEffect, useState } from 'react'
import { useSearch } from '@tanstack/react-router'
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
  const { token } = useSearch({ strict: false }) as { token?: string }
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const establishSession = useSessionStore((s) => s.establishSession)

  useEffect(() => {
    async function handleCallback() {
      console.log('[AuthCallback] search token:', token)

      if (!token) {
        console.error('[AuthCallback] no token in search params')
        setError('Authentication failed. Please try again.')
        setLoading(false)
        return
      }

      const userId = decodeJwtSub(token)
      console.log('[AuthCallback] decoded userId:', userId)

      if (!userId) {
        console.error('[AuthCallback] could not decode userId from token')
        setError('Authentication failed. Please try again.')
        setLoading(false)
        return
      }

      try {
        console.log('[AuthCallback] fetching user profile for:', userId)
        const userResponse = await fetchUserById(userId, token)
        console.log('[AuthCallback] user fetched successfully:', userResponse)
        establishSession(userResponse, token)
        onSuccess()
      } catch (e) {
        console.error('[AuthCallback] fetchUserById failed:', e)
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
