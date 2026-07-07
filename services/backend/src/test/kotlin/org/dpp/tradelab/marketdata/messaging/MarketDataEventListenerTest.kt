package org.dpp.tradelab.marketdata.messaging

import io.kotest.core.spec.style.FunSpec
import org.dpp.tradelab.marketdata.service.MarketDataFeedService
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for [MarketDataEventListener].
 *
 * Asserts that each listener method delegates to the correct [MarketDataFeedService]
 * `handle*` method with the exact event payload — and does nothing else.
 */
class MarketDataEventListenerTest : FunSpec({

    val marketDataFeedService = mock<MarketDataFeedService>()
    val listener = MarketDataEventListener(marketDataFeedService)

    test("onAssetSubscribed_withEvent_delegatesToHandleAssetSubscribed") {
        val event = AssetSubscribedEvent(userId = UUID.randomUUID(), tickers = listOf("AAPL", "MSFT"), timestamp = Instant.now())

        listener.onAssetSubscribed(event)

        verify(marketDataFeedService).handleAssetSubscribed(event)
    }

    test("onAssetUnsubscribed_withEvent_delegatesToHandleAssetUnsubscribed") {
        val event = AssetUnsubscribedEvent(userId = UUID.randomUUID(), tickers = listOf("AAPL"), timestamp = Instant.now())

        listener.onAssetUnsubscribed(event)

        verify(marketDataFeedService).handleAssetUnsubscribed(event)
    }
})
