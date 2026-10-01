import { useState, useEffect, useRef } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { connectMarketDataFeed } from '../api/marketDataFeedApi'
import type { MarketDataUpdate, FeedMessage } from '../api/marketDataFeedApi'
import { useSessionStore, isTokenExpired } from '../../user/hooks/useSessionStore'

type FeedStatus = 'connecting' | 'connected' | 'error' | 'lost'

const RETRY_DELAYS_MS = [2000, 5000, 10000, 30000]

// WebSocket close code used by the backend when the JWT is missing/expired/invalid.
const AUTH_CLOSE_CODE = 4401

export function useMarketDataFeed(
  _userId: string,
  subscribedTickers: string[],
): {
  rows: MarketDataUpdate[]
  feedStatus: FeedStatus
} {
  const [rows, setRows] = useState<MarketDataUpdate[]>([])
  const [feedStatus, setFeedStatus] = useState<FeedStatus>('connecting')
  const [retryCount, setRetryCount] = useState(0)
  const cleanupRef = useRef<(() => void) | null>(null)
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const accessToken = useSessionStore((s) => s.session?.accessToken)
  const clearSession = useSessionStore((s) => s.clearSession)
  const navigate = useNavigate()

  useEffect(() => {
    if (!accessToken) return

    // The token may have expired while the app was open. The store only checks
    // expiry at page load, so re-check here before (re)connecting — otherwise we
    // would hand the backend a dead token and loop on the 4401 close forever.
    const forceReLogin = () => {
      clearSession()
      navigate({ to: '/login', replace: true })
    }

    if (isTokenExpired(accessToken)) {
      forceReLogin()
      return
    }

    setFeedStatus('connecting')

    const scheduleRetry = (attempt: number) => {
      const delayMs = RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)]
      retryTimerRef.current = setTimeout(() => {
        setRetryCount((c) => c + 1)
      }, delayMs)
    }

    const cleanup = connectMarketDataFeed(
      accessToken,
      (msg: FeedMessage) => {
        if (msg.type === 'SNAPSHOT') {
          setRows(msg.data)
          setFeedStatus('connected')
        } else {
          // TICK
          setRows((prev) => {
            const idx = prev.findIndex((r) => r.ticker === msg.data.ticker)
            if (idx === -1) {
              return [...prev, msg.data]
            }
            const next = [...prev]
            next[idx] = msg.data
            return next
          })
          setFeedStatus('connected')
        }
      },
      (code: number) => {
        // Backend rejected the token (expired/invalid) — stop retrying and
        // force the user back through login to obtain a fresh token.
        if (code === AUTH_CLOSE_CODE) {
          forceReLogin()
          return
        }
        setFeedStatus('lost')
        scheduleRetry(retryCount)
      },
      () => {
        setFeedStatus('lost')
        scheduleRetry(retryCount)
      },
    )

    cleanupRef.current = cleanup

    return () => {
      cleanup()
      cleanupRef.current = null
      if (retryTimerRef.current !== null) {
        clearTimeout(retryTimerRef.current)
        retryTimerRef.current = null
      }
    }
  }, [accessToken, retryCount, clearSession, navigate])

  useEffect(() => {
    if (subscribedTickers.length === 0) return // not loaded yet — do nothing
    setRows((prev) => prev.filter((r) => subscribedTickers.includes(r.ticker)))
  }, [subscribedTickers])

  return { rows, feedStatus }
}
