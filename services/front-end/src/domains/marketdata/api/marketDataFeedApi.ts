export interface MarketDataUpdate {
  ticker: string
  companyName: string
  currentPrice: number
  open: number
  dayLow: number
  fiftyTwoWeekHigh: number
}

export interface SnapshotMessage {
  type: 'SNAPSHOT'
  data: MarketDataUpdate[]
}

export interface TickMessage {
  type: 'TICK'
  data: MarketDataUpdate
}

export type FeedMessage = SnapshotMessage | TickMessage

export function connectMarketDataFeed(
  userId: string,
  onMessage: (msg: FeedMessage) => void,
  onError: (code: number) => void,
  onClose: () => void,
): () => void {
  const url = `ws://${window.location.host}/api/v1/market-data/feed?userId=${userId}`

  let aborted = false
  let activeSocket: WebSocket = openSocket(url, false)

  function openSocket(socketUrl: string, isReconnect: boolean): WebSocket {
    const ws = new WebSocket(socketUrl)

    ws.onmessage = (event: MessageEvent<string>) => {
      if (aborted) return
      const msg = JSON.parse(event.data) as FeedMessage
      onMessage(msg)
    }

    ws.onclose = (event: CloseEvent) => {
      if (aborted) return
      if (event.code === 1000) {
        onClose()
      } else if (!isReconnect) {
        // First unexpected close — attempt one reconnect
        activeSocket = openSocket(socketUrl, true)
      } else {
        // Reconnect also closed unexpectedly — call onError, no further retries
        onError(event.code)
      }
    }

    return ws
  }

  return () => {
    aborted = true
    activeSocket.close(1000)
  }
}
