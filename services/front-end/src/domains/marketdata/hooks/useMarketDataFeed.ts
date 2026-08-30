import { useState, useEffect, useRef } from 'react'
import { connectMarketDataFeed } from '../api/marketDataFeedApi'
import type { MarketDataUpdate, FeedMessage } from '../api/marketDataFeedApi'

type FeedStatus = 'connecting' | 'connected' | 'error' | 'lost'

const RETRY_DELAYS_MS = [2000, 5000, 10000, 30000]

export function useMarketDataFeed(
  userId: string,
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

  useEffect(() => {
    if (!userId) return

    setFeedStatus('connecting')

    const scheduleRetry = (attempt: number) => {
      const delayMs = RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)]
      retryTimerRef.current = setTimeout(() => {
        setRetryCount((c) => c + 1)
      }, delayMs)
    }

    const cleanup = connectMarketDataFeed(
      userId,
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
      (_code: number) => {
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
  }, [userId, retryCount])

  useEffect(() => {
    if (subscribedTickers.length === 0) return // not loaded yet — do nothing
    setRows((prev) => prev.filter((r) => subscribedTickers.includes(r.ticker)))
  }, [subscribedTickers])

  return { rows, feedStatus }
}
