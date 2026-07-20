import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { connectMarketDataFeed } from './marketDataFeedApi'
import type { FeedMessage, SnapshotMessage, TickMessage } from './marketDataFeedApi'

// ------- WebSocket mock -------

interface MockWebSocketInstance {
  url: string
  close: ReturnType<typeof vi.fn>
  simulateMessage: (data: FeedMessage) => void
  simulateClose: (code: number) => void
}

let instances: MockWebSocketInstance[] = []

class MockWebSocketClass {
  url: string
  onmessage: ((event: MessageEvent<string>) => void) | null = null
  onclose: ((event: CloseEvent) => void) | null = null
  close = vi.fn()

  constructor(url: string) {
    this.url = url
    instances.push({
      url: this.url,
      close: this.close,
      simulateMessage: (data: FeedMessage) => {
        this.onmessage?.({ data: JSON.stringify(data) } as MessageEvent<string>)
      },
      simulateClose: (code: number) => {
        this.onclose?.({ code } as CloseEvent)
      },
    })
  }
}

describe('connectMarketDataFeed', () => {
  beforeEach(() => {
    instances = []
    vi.stubGlobal('WebSocket', MockWebSocketClass)
    Object.defineProperty(window, 'location', {
      value: { host: 'localhost:3000' },
      writable: true,
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('connectMarketDataFeed - called with userId - opens WebSocket with correct URL', () => {
    connectMarketDataFeed('user-123', vi.fn(), vi.fn(), vi.fn())
    expect(instances).toHaveLength(1)
    expect(instances[0].url).toBe('ws://localhost:3000/api/v1/market-data/feed?userId=user-123')
  })

  it('connectMarketDataFeed - SNAPSHOT message received - calls onMessage with parsed SNAPSHOT', () => {
    const onMessage = vi.fn()
    connectMarketDataFeed('user-1', onMessage, vi.fn(), vi.fn())

    const snapshot: SnapshotMessage = {
      type: 'SNAPSHOT',
      data: [
        {
          ticker: 'AAPL',
          companyName: 'Apple Inc.',
          currentPrice: 180.5,
          open: 179.0,
          dayLow: 178.0,
          fiftyTwoWeekHigh: 200.0,
        },
      ],
    }
    instances[0].simulateMessage(snapshot)

    expect(onMessage).toHaveBeenCalledOnce()
    expect(onMessage).toHaveBeenCalledWith(snapshot)
  })

  it('connectMarketDataFeed - TICK message received - calls onMessage with parsed TICK', () => {
    const onMessage = vi.fn()
    connectMarketDataFeed('user-2', onMessage, vi.fn(), vi.fn())

    const tick: TickMessage = {
      type: 'TICK',
      data: {
        ticker: 'MSFT',
        companyName: 'Microsoft Corporation',
        currentPrice: 300.0,
        open: 298.0,
        dayLow: 295.0,
        fiftyTwoWeekHigh: 350.0,
      },
    }
    instances[0].simulateMessage(tick)

    expect(onMessage).toHaveBeenCalledOnce()
    expect(onMessage).toHaveBeenCalledWith(tick)
  })

  it('connectMarketDataFeed - unexpected close on first socket - attempts exactly one reconnect', () => {
    connectMarketDataFeed('user-3', vi.fn(), vi.fn(), vi.fn())
    expect(instances).toHaveLength(1)

    // Simulate unexpected close
    instances[0].simulateClose(1006)

    // Should have created a second (reconnect) socket
    expect(instances).toHaveLength(2)
    expect(instances[1].url).toBe('ws://localhost:3000/api/v1/market-data/feed?userId=user-3')
  })

  it('connectMarketDataFeed - reconnect socket also closes unexpectedly - calls onError with code', () => {
    const onError = vi.fn()
    connectMarketDataFeed('user-4', vi.fn(), onError, vi.fn())

    // First unexpected close → reconnect
    instances[0].simulateClose(1006)
    // Reconnect also closes unexpectedly
    instances[1].simulateClose(1006)

    // No third socket created
    expect(instances).toHaveLength(2)
    expect(onError).toHaveBeenCalledOnce()
    expect(onError).toHaveBeenCalledWith(1006)
  })

  it('connectMarketDataFeed - cleanup function called - closes active socket with code 1000', () => {
    const cleanup = connectMarketDataFeed('user-5', vi.fn(), vi.fn(), vi.fn())
    cleanup()
    expect(instances[0].close).toHaveBeenCalledOnce()
    expect(instances[0].close).toHaveBeenCalledWith(1000)
  })

  it('connectMarketDataFeed - cleanup after reconnect - closes reconnect socket with code 1000', () => {
    const cleanup = connectMarketDataFeed('user-6', vi.fn(), vi.fn(), vi.fn())
    // Trigger reconnect
    instances[0].simulateClose(1006)
    // Now cleanup should target the reconnect socket
    cleanup()
    expect(instances[1].close).toHaveBeenCalledWith(1000)
  })

  it('connectMarketDataFeed - clean close (code 1000) - calls onClose and does not reconnect', () => {
    const onClose = vi.fn()
    connectMarketDataFeed('user-7', vi.fn(), vi.fn(), onClose)

    instances[0].simulateClose(1000)

    expect(onClose).toHaveBeenCalledOnce()
    // No reconnect attempt
    expect(instances).toHaveLength(1)
  })
})
