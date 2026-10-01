// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useMarketDataFeed } from './useMarketDataFeed'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import type { UserResponse } from '../../user/types/user'

vi.mock('../api/marketDataFeedApi', () => ({
  connectMarketDataFeed: vi.fn(),
}))

const mockNavigate = vi.fn()
vi.mock('@tanstack/react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-router')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

import { connectMarketDataFeed } from '../api/marketDataFeedApi'
import type { FeedMessage } from '../api/marketDataFeedApi'

const mockConnect = vi.mocked(connectMarketDataFeed)

// Builds a JWT-shaped token whose payload carries the given `exp` (seconds).
// Only the payload segment needs to decode — isTokenExpired ignores the rest.
function makeJwt(expSeconds: number): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const payload = btoa(JSON.stringify({ exp: expSeconds }))
  return `${header}.${payload}.sig`
}
const FUTURE_EXP = Math.floor(Date.now() / 1000) + 3600
const EXPIRED_EXP = Math.floor(Date.now() / 1000) - 3600

// Stable ticker arrays declared outside tests so their reference never changes
// between re-renders.  If a new array literal were passed inline to renderHook's
// callback, React would see a different reference on every render and the
// subscribedTickers useEffect would fire in an infinite loop → OOM crash.
const AAPL_MSFT = ['AAPL', 'MSFT']
const AAPL_GOOG = ['AAPL', 'GOOG']
const AAPL_ONLY = ['AAPL']
const SESSION_TOKEN = makeJwt(FUTURE_EXP)
const MOCK_USER: UserResponse = {
  userId: 'session-user',
  firstName: 'Test',
  lastName: 'User',
  address: null,
  email: 'test@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
  settings: { feedType: 'SYNTHETIC', updatedAt: '2026-01-01T00:00:00Z' },
}

describe('useMarketDataFeed', () => {
  let capturedOnMessage: (msg: FeedMessage) => void
  let capturedOnError: (code: number) => void
  let capturedOnClose: () => void
  let mockCleanup: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    mockCleanup = vi.fn()
    act(() => {
      useSessionStore.getState().clearSession()
      useSessionStore.getState().establishSession(MOCK_USER, SESSION_TOKEN)
    })
    mockConnect.mockImplementation(
      (
        _token: string,
        onMessage: (msg: FeedMessage) => void,
        onError: (code: number) => void,
        onClose: () => void,
      ) => {
        capturedOnMessage = onMessage
        capturedOnError = onError
        capturedOnClose = onClose
        return mockCleanup as unknown as () => void
      },
    )
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('useMarketDataFeed - SNAPSHOT received - populates rows and sets feedStatus to connected', () => {
    const { result } = renderHook(() => useMarketDataFeed('user-1', AAPL_MSFT))

    expect(result.current.feedStatus).toBe('connecting')

    act(() => {
      capturedOnMessage({
        type: 'SNAPSHOT',
        data: [
          {
            ticker: 'AAPL',
            companyName: 'Apple Inc.',
            currentPrice: 180.5,
            open: 179.0,
            dayLow: 178.0,
            dayHigh: 182.0,
            fiftyTwoWeekHigh: 200.0,
          },
          {
            ticker: 'MSFT',
            companyName: 'Microsoft Corporation',
            currentPrice: 300.0,
            open: 298.0,
            dayLow: 295.0,
            dayHigh: 305.0,
            fiftyTwoWeekHigh: 350.0,
          },
        ],
      })
    })

    expect(result.current.feedStatus).toBe('connected')
    expect(result.current.rows).toHaveLength(2)
    expect(result.current.rows[0].ticker).toBe('AAPL')
    expect(result.current.rows[1].ticker).toBe('MSFT')
  })

  it('useMarketDataFeed - session has an access token - connects using token instead of userId', () => {
    renderHook(() => useMarketDataFeed('caller-user-id', AAPL_ONLY))

    expect(mockConnect).toHaveBeenCalledOnce()
    expect(mockConnect.mock.calls[0][0]).toBe(SESSION_TOKEN)
    expect(mockConnect.mock.calls[0][0]).not.toBe('caller-user-id')
  })

  it('useMarketDataFeed - session has no access token - does not connect', () => {
    act(() => useSessionStore.getState().clearSession())

    renderHook(() => useMarketDataFeed('caller-user-id', AAPL_ONLY))

    expect(mockConnect).not.toHaveBeenCalled()
  })

  it('useMarketDataFeed - access token expired - forces re-login and does not connect', () => {
    act(() => {
      useSessionStore.getState().clearSession()
      useSessionStore.getState().establishSession(MOCK_USER, makeJwt(EXPIRED_EXP))
    })

    renderHook(() => useMarketDataFeed('caller-user-id', AAPL_ONLY))

    expect(mockConnect).not.toHaveBeenCalled()
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/login', replace: true })
    expect(useSessionStore.getState().session).toBeNull()
  })

  it('useMarketDataFeed - onError 4401 (auth rejected) - forces re-login and stops retrying', () => {
    renderHook(() => useMarketDataFeed('user-auth', AAPL_ONLY))
    expect(mockConnect).toHaveBeenCalledTimes(1)

    act(() => { capturedOnError(4401) })

    expect(mockNavigate).toHaveBeenCalledWith({ to: '/login', replace: true })
    expect(useSessionStore.getState().session).toBeNull()

    // No retry is scheduled — advancing past every backoff delay re-connects nothing.
    act(() => { vi.advanceTimersByTime(30000) })
    expect(mockConnect).toHaveBeenCalledTimes(1)
  })

  it('useMarketDataFeed - TICK for existing ticker - updates that row in place', () => {
    const { result } = renderHook(() => useMarketDataFeed('user-2', AAPL_MSFT))

    act(() => {
      capturedOnMessage({
        type: 'SNAPSHOT',
        data: [
          {
            ticker: 'AAPL',
            companyName: 'Apple Inc.',
            currentPrice: 180.5,
            open: 179.0,
            dayLow: 178.0,
            dayHigh: 182.0,
            fiftyTwoWeekHigh: 200.0,
          },
          {
            ticker: 'MSFT',
            companyName: 'Microsoft Corporation',
            currentPrice: 300.0,
            open: 298.0,
            dayLow: 295.0,
            dayHigh: 305.0,
            fiftyTwoWeekHigh: 350.0,
          },
        ],
      })
    })

    act(() => {
      capturedOnMessage({
        type: 'TICK',
        data: {
          ticker: 'AAPL',
          companyName: 'Apple Inc.',
          currentPrice: 185.0,
          open: 179.0,
          dayLow: 178.0,
          dayHigh: 185.0,
          fiftyTwoWeekHigh: 200.0,
        },
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
          {
            ticker: 'AAPL',
            companyName: 'Apple Inc.',
            currentPrice: 180.5,
            open: 179.0,
            dayLow: 178.0,
            dayHigh: 182.0,
            fiftyTwoWeekHigh: 200.0,
          },
        ],
      })
    })

    act(() => {
      capturedOnMessage({
        type: 'TICK',
        data: {
          ticker: 'GOOG',
          companyName: 'Alphabet Inc.',
          currentPrice: 150.0,
          open: 148.0,
          dayLow: 145.0,
          dayHigh: 155.0,
          fiftyTwoWeekHigh: 180.0,
        },
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
          {
            ticker: 'AAPL',
            companyName: 'Apple Inc.',
            currentPrice: 180.5,
            open: 179.0,
            dayLow: 178.0,
            dayHigh: 182.0,
            fiftyTwoWeekHigh: 200.0,
          },
          {
            ticker: 'MSFT',
            companyName: 'Microsoft Corporation',
            currentPrice: 300.0,
            open: 298.0,
            dayLow: 295.0,
            dayHigh: 305.0,
            fiftyTwoWeekHigh: 350.0,
          },
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

    act(() => { capturedOnError(1006) })

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

  it('useMarketDataFeed - same subscribedTickers reference on rerender - does NOT re-run filter setRows', () => {
    // Regression test for bug #61.
    const { result, rerender } = renderHook(
      ({ tickers }: { tickers: string[] }) => useMarketDataFeed('user-8', tickers),
      { initialProps: { tickers: AAPL_MSFT } },
    )

    act(() => {
      capturedOnMessage({
        type: 'SNAPSHOT',
        data: [
          {
            ticker: 'AAPL',
            companyName: 'Apple Inc.',
            currentPrice: 180.5,
            open: 179.0,
            dayLow: 178.0,
            dayHigh: 182.0,
            fiftyTwoWeekHigh: 200.0,
          },
          {
            ticker: 'MSFT',
            companyName: 'Microsoft Corporation',
            currentPrice: 300.0,
            open: 298.0,
            dayLow: 295.0,
            dayHigh: 305.0,
            fiftyTwoWeekHigh: 350.0,
          },
        ],
      })
    })

    expect(result.current.rows).toHaveLength(2)
    const rowsBeforeRerender = result.current.rows

    rerender({ tickers: AAPL_MSFT })

    expect(result.current.rows).toBe(rowsBeforeRerender)
    expect(result.current.rows).toHaveLength(2)
  })

  it('useMarketDataFeed - onError fires - reconnects after 2s delay', async () => {
    const { result } = renderHook(() => useMarketDataFeed('user-9', AAPL_ONLY))
    expect(mockConnect).toHaveBeenCalledTimes(1)

    act(() => {
      capturedOnError(1006)
    })
    expect(result.current.feedStatus).toBe('lost')
    expect(mockConnect).toHaveBeenCalledTimes(1)

    // Advance past the first retry delay (2000ms) — this triggers the setTimeout
    // callback which calls setRetryCount, re-running the effect synchronously
    act(() => { vi.advanceTimersByTime(2001) })

    expect(mockConnect).toHaveBeenCalledTimes(2)
    expect(result.current.feedStatus).toBe('connecting')
  })

  it('useMarketDataFeed - onClose fires - reconnects after 2s delay', async () => {
    const { result } = renderHook(() => useMarketDataFeed('user-10', AAPL_ONLY))
    expect(mockConnect).toHaveBeenCalledTimes(1)

    act(() => {
      capturedOnClose()
    })
    expect(result.current.feedStatus).toBe('lost')

    act(() => {
      vi.advanceTimersByTime(2001)
    })

    expect(mockConnect).toHaveBeenCalledTimes(2)
  })

  it('useMarketDataFeed - reconnect succeeds - resets retryCount and sets connected', () => {
    const { result } = renderHook(() => useMarketDataFeed('user-11', AAPL_ONLY))

    // Trigger a failure on the first connection
    act(() => {
      capturedOnError(1006)
    })
    expect(result.current.feedStatus).toBe('lost')

    // Advance timer — retry fires, second connect call is made, status → connecting
    act(() => {
      vi.advanceTimersByTime(2001)
    })
    expect(mockConnect).toHaveBeenCalledTimes(2)
    expect(result.current.feedStatus).toBe('connecting')

    // Send a SNAPSHOT on the new connection — capturedOnMessage is re-assigned by
    // the second mockConnect call inside the same act above
    act(() => {
      capturedOnMessage({
        type: 'SNAPSHOT',
        data: [{
          ticker: 'AAPL',
          companyName: 'Apple Inc.',
          currentPrice: 180.5,
          open: 179.0,
          dayLow: 178.0,
          dayHigh: 182.0,
          fiftyTwoWeekHigh: 200.0,
        }],
      })
    })

    expect(result.current.feedStatus).toBe('connected')
    expect(result.current.rows).toHaveLength(1)
  })

  it('useMarketDataFeed - unmount during retry delay - cancels pending timer', () => {
    const { unmount } = renderHook(() => useMarketDataFeed('user-12', AAPL_ONLY))

    act(() => { capturedOnError(1006) })
    unmount()

    // Advance past the retry delay — connectMarketDataFeed should NOT be called again
    act(() => { vi.advanceTimersByTime(5000) })
    expect(mockConnect).toHaveBeenCalledTimes(1)
  })
})

describe('useMarketDataFeed', () => {
  let capturedOnMessage: (msg: FeedMessage) => void
  let capturedOnError: (code: number) => void
  let capturedOnClose: () => void
  let mockCleanup: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.clearAllMocks()
    mockCleanup = vi.fn()
    act(() => {
      useSessionStore.getState().clearSession()
      useSessionStore.getState().establishSession(MOCK_USER, SESSION_TOKEN)
    })
    mockConnect.mockImplementation(
      (
        _token: string,
        onMessage: (msg: FeedMessage) => void,
        onError: (code: number) => void,
        onClose: () => void,
      ) => {
        capturedOnMessage = onMessage
        capturedOnError = onError
        capturedOnClose = onClose
        return mockCleanup as unknown as () => void
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
          {
            ticker: 'AAPL',
            companyName: 'Apple Inc.',
            currentPrice: 180.5,
            open: 179.0,
            dayLow: 178.0,
            dayHigh: 182.0,
            fiftyTwoWeekHigh: 200.0,
          },
          {
            ticker: 'MSFT',
            companyName: 'Microsoft Corporation',
            currentPrice: 300.0,
            open: 298.0,
            dayLow: 295.0,
            dayHigh: 305.0,
            fiftyTwoWeekHigh: 350.0,
          },
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
          {
            ticker: 'AAPL',
            companyName: 'Apple Inc.',
            currentPrice: 180.5,
            open: 179.0,
            dayLow: 178.0,
            dayHigh: 182.0,
            fiftyTwoWeekHigh: 200.0,
          },
          {
            ticker: 'MSFT',
            companyName: 'Microsoft Corporation',
            currentPrice: 300.0,
            open: 298.0,
            dayLow: 295.0,
            dayHigh: 305.0,
            fiftyTwoWeekHigh: 350.0,
          },
        ],
      })
    })

    act(() => {
      capturedOnMessage({
        type: 'TICK',
        data: {
          ticker: 'AAPL',
          companyName: 'Apple Inc.',
          currentPrice: 185.0,
          open: 179.0,
          dayLow: 178.0,
          dayHigh: 185.0,
          fiftyTwoWeekHigh: 200.0,
        },
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
          {
            ticker: 'AAPL',
            companyName: 'Apple Inc.',
            currentPrice: 180.5,
            open: 179.0,
            dayLow: 178.0,
            dayHigh: 182.0,
            fiftyTwoWeekHigh: 200.0,
          },
        ],
      })
    })

    act(() => {
      capturedOnMessage({
        type: 'TICK',
        data: {
          ticker: 'GOOG',
          companyName: 'Alphabet Inc.',
          currentPrice: 150.0,
          open: 148.0,
          dayLow: 145.0,
          dayHigh: 155.0,
          fiftyTwoWeekHigh: 180.0,
        },
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
          {
            ticker: 'AAPL',
            companyName: 'Apple Inc.',
            currentPrice: 180.5,
            open: 179.0,
            dayLow: 178.0,
            dayHigh: 182.0,
            fiftyTwoWeekHigh: 200.0,
          },
          {
            ticker: 'MSFT',
            companyName: 'Microsoft Corporation',
            currentPrice: 300.0,
            open: 298.0,
            dayLow: 295.0,
            dayHigh: 305.0,
            fiftyTwoWeekHigh: 350.0,
          },
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

  it('useMarketDataFeed - same subscribedTickers reference on rerender - does NOT re-run filter setRows', () => {
    // Regression test for bug #61.
    const { result, rerender } = renderHook(
      ({ tickers }: { tickers: string[] }) => useMarketDataFeed('user-8', tickers),
      { initialProps: { tickers: AAPL_MSFT } },
    )

    act(() => {
      capturedOnMessage({
        type: 'SNAPSHOT',
        data: [
          {
            ticker: 'AAPL',
            companyName: 'Apple Inc.',
            currentPrice: 180.5,
            open: 179.0,
            dayLow: 178.0,
            dayHigh: 182.0,
            fiftyTwoWeekHigh: 200.0,
          },
          {
            ticker: 'MSFT',
            companyName: 'Microsoft Corporation',
            currentPrice: 300.0,
            open: 298.0,
            dayLow: 295.0,
            dayHigh: 305.0,
            fiftyTwoWeekHigh: 350.0,
          },
        ],
      })
    })

    expect(result.current.rows).toHaveLength(2)
    const rowsBeforeRerender = result.current.rows

    rerender({ tickers: AAPL_MSFT })

    expect(result.current.rows).toBe(rowsBeforeRerender)
    expect(result.current.rows).toHaveLength(2)
  })
})
