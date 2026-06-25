import { useState, useEffect, useRef } from 'react'
import { connectMarketDataFeed } from '../api/marketDataFeedApi'
import type { MarketDataUpdate, FeedMessage } from '../api/marketDataFeedApi'

type FeedStatus = 'connecting' | 'connected' | 'error' | 'lost'

export function useMarketDataFeed(
  userId: string,
  subscribedTickers: string[],
): {
  rows: MarketDataUpdate[]
  feedStatus: FeedStatus
} {
  const [rows, setRows] = useState<MarketDataUpdate[]>([])
  const [feedStatus, setFeedStatus] = useState<FeedStatus>('connecting')
  const cleanupRef = useRef<(() => void) | null>(null)

  useEffect(() => {
    setFeedStatus('connecting')

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
      },
      () => {
        setFeedStatus('lost')
      },
    )

    cleanupRef.current = cleanup

    return () => {
      cleanup()
      cleanupRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId])

  useEffect(() => {
    setRows((prev) => prev.filter((r) => subscribedTickers.includes(r.ticker)))
  }, [subscribedTickers])

  return { rows, feedStatus }
}
