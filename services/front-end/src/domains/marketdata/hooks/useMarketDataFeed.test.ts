// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useMarketDataFeed } from './useMarketDataFeed'

vi.mock('../api/marketDataFeedApi', () => ({
  connectMarketDataFeed: vi.fn(),
}))

import { connectMarketDataFeed } from '../api/marketDataFeedApi'
import type { FeedMessage } from '../api/marketDataFeedApi'

const mockConnect = vi.mocked(connectMarketDataFeed)

// Stable ticker arrays declared outside tests so their reference never changes
// between re-renders.  If a new array literal were passed inline to renderHook's
// callback, React would see a different reference on every render and the
// subscribedTickers useEffect would fire in an infinite loop → OOM crash.
const AAPL_MSFT = ['AAPL', 'MSFT']
const AAPL_GOOG = ['AAPL', 'GOOG']
const AAPL_ONLY = ['AAPL']

describe('useMarketDataFeed', () => {
  let capturedOnMessage: (msg: FeedMessage) => void
  let capturedOnError: (code: number) => void
  let capturedOnClose: () => void
  let mockCleanup: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.clearAllMocks()
    mockCleanup = vi.fn()
    mockConnect.mockImplementation(
      (
        _userId: string,
        onMessage: (msg: FeedMessage) => void,
        onError: (code: number) => void,
        onClose: () => void,
      ) => {
        capturedOnMessage = onMessage
        capturedOnError = onError
        capturedOnClose = onClose
        return mockCleanup
      },
    )
  })

  it('useMarketDataFeed - SNAPSHOT received - populates rows and sets feedStatus to connected', () => {
    const { result } = renderHook(() => useMarketDataFeed('user-1', AAPL_MSFT))

    expect(result.current.feedStatus).toBe('connecting')

    act(() => {
      capturedOnMessage({
        type: 'SNAPSHOT',
        data: [
          { ticker: 'AAPL', companyName: 'Apple Inc.', currentPrice: 180.5, open: 179.0, dayLow: 178.0, fiftyTwoWeekHigh: 200.0 },
          { ticker: 'MSFT', companyName: 'Microsoft Corporation', currentPrice: 300.0, open: 298.0, dayLow: 295.0, fiftyTwoWeekHigh: 350.0 },
        ],
      })
    })

    expect(result.current.feedStatus).toBe('connected')
    expect(result.current.rows).toHaveLength(2)
    expect(result.current.rows[0].ticker).toBe('AAPL')
    expect(result.current.rows[1].ticker).toBe('MSFT')
  })

  it('useMarketDataFeed - TICK for existing ticker - updates that row in place', () => {
    const { result } = renderHook(() => useMarketDataFeed('user-2', AAPL_MSFT))

    act(() => {
      capturedOnMessage({
        type: 'SNAPSHOT',
        data: [
          { ticker: 'AAPL', companyName: 'Apple Inc.', currentPrice: 180.5, open: 179.0, dayLow: 178.0, fiftyTwoWeekHigh: 200.0 },
          { ticker: 'MSFT', companyName: 'Microsoft Corporation', currentPrice: 300.0, open: 298.0, dayLow: 295.0, fiftyTwoWeekHigh: 350.0 },
        ],
      })
    })

    act(() => {
      capturedOnMessage({
        type: 'TICK',
        data: { ticker: 'AAPL', companyName: 'Apple Inc.', currentPrice: 185.0, open: 179.0, dayLow: 178.0, fiftyTwoWeekHigh: 200.0 },
      })
    })

    expect(result.current.rows).toHaveLength(2)
    const aapl = result.current.rows.find((r) => r.ticker === 'AAPL')
    expect(aapl?.currentPrice).toBe(185.0)
    const msft = result.current.rows.find((r) => r.ticker === 'MSFT')
    expect(msft?.currentPrice).toBe(300.0)
  })

  it('useMarketDataFeed - TICK for unknown ticker - appends row', () => {
    const { result } = renderHook(() => useMarketDataFeed('user-3', AAPL_GOOG))

    act(() => {
      capturedOnMessage({
        type: 'SNAPSHOT',
        data: [
          { ticker: 'AAPL', companyName: 'Apple Inc.', currentPrice: 180.5, open: 179.0, dayLow: 178.0, fiftyTwoWeekHigh: 200.0 },
        ],
      })
    })

    act(() => {
      capturedOnMessage({
        type: 'TICK',
        data: { ticker: 'GOOG', companyName: 'Alphabet Inc.', currentPrice: 150.0, open: 148.0, dayLow: 145.0, fiftyTwoWeekHigh: 180.0 },
      })
    })

    expect(result.current.rows).toHaveLength(2)
    expect(result.current.rows[1].ticker).toBe('GOOG')
  })

  it('useMarketDataFeed - subscribedTickers changes - removes rows not in new ticker list', () => {
    const { result, rerender } = renderHook(
      ({ tickers }: { tickers: string[] }) => useMarketDataFeed('user-4', tickers),
      { initialProps: { tickers: AAPL_MSFT } },
    )

    act(() => {
      capturedOnMessage({
        type: 'SNAPSHOT',
        data: [
          { ticker: 'AAPL', companyName: 'Apple Inc.', currentPrice: 180.5, open: 179.0, dayLow: 178.0, fiftyTwoWeekHigh: 200.0 },
          { ticker: 'MSFT', companyName: 'Microsoft Corporation', currentPrice: 300.0, open: 298.0, dayLow: 295.0, fiftyTwoWeekHigh: 350.0 },
        ],
      })
    })

    expect(result.current.rows).toHaveLength(2)

    rerender({ tickers: AAPL_ONLY })

    expect(result.current.rows).toHaveLength(1)
    expect(result.current.rows[0].ticker).toBe('AAPL')
  })

  it('useMarketDataFeed - onError callback fires - sets feedStatus to lost', () => {
    const { result } = renderHook(() => useMarketDataFeed('user-5', AAPL_ONLY))

    act(() => {
      capturedOnError(1006)
    })

    expect(result.current.feedStatus).toBe('lost')
  })

  it('useMarketDataFeed - onClose callback fires - sets feedStatus to lost', () => {
    const { result } = renderHook(() => useMarketDataFeed('user-6', AAPL_ONLY))

    act(() => {
      capturedOnClose()
    })

    expect(result.current.feedStatus).toBe('lost')
  })

  it('useMarketDataFeed - component unmounts - calls cleanup function', () => {
    const { unmount } = renderHook(() => useMarketDataFeed('user-7', AAPL_ONLY))
    unmount()
    expect(mockCleanup).toHaveBeenCalledOnce()
  })
})
