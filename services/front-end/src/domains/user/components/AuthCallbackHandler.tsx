import axios from 'axios'
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
  const search = useSearch({ strict: false }) as Record<string, string | undefined>
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const establishSession = useSessionStore((s) => s.establishSession)

  useEffect(() => {
    async function handleCallback() {
      const token = search?.token ?? null

      if (!token) {
        console.error('[AuthCallback] FAIL: no token in URL')
        console.groupEnd()
        setError('Authentication failed. Please try again.')
        setLoading(false)
        return
      }

      // ── Step 2: JWT decode ─────────────────────────────────────────────────
      let rawPayload: unknown = null
      try {
        rawPayload = JSON.parse(atob(token.split('.')[1]))
        console.log('[AuthCallback] decoded JWT payload:', rawPayload)
      } catch (e) {
        console.error('[AuthCallback] FAIL: could not base64-decode JWT payload', e)
      }

      const userId = decodeJwtSub(token)
      console.log('[AuthCallback] decoded userId (sub):', userId ?? 'NULL — decode failed')

      if (!userId) {
        console.error('[AuthCallback] FAIL: sub missing from JWT')
        console.groupEnd()
        setError('Authentication failed. Please try again.')
        setLoading(false)
        return
      }

      // ── Step 3: fetch user profile ─────────────────────────────────────────
      console.log(`[AuthCallback] calling GET /v1/users/${userId} with explicit Bearer token`)
      try {
        const userResponse = await fetchUserById(userId, token)
        console.log('[AuthCallback] fetchUserById SUCCESS:', userResponse)
        establishSession(userResponse, token)
        console.log('[AuthCallback] session established — navigating to /trade')
        console.groupEnd()
        onSuccess()
      } catch (err: unknown) {
        if (axios.isAxiosError(err)) {
          console.error(
            '[AuthCallback] FAIL: fetchUserById HTTP error',
            '\n  status :', err.response?.status,
            '\n  statusText:', err.response?.statusText,
            '\n  url :', err.config?.url,
            '\n  baseURL :', err.config?.baseURL,
            '\n  headers sent:', err.config?.headers,
            '\n  response body:', err.response?.data,
          )
        } else {
          console.error('[AuthCallback] FAIL: non-HTTP error during fetchUserById', err)
        }
        console.groupEnd()
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
