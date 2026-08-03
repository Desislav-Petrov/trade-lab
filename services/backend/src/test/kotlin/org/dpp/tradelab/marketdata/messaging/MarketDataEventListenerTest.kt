package org.dpp.tradelab.marketdata.messaging

import io.kotest.core.spec.style.FunSpec
import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.dpp.tradelab.marketdata.service.MarketDataFeedService
import org.dpp.tradelab.user.model.FeedType
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.math.BigDecimal
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

    test("onMarketDataTick_withEvent_delegatesToHandleMarketDataTick") {
        val snapshot = MarketDataSnapshot(
            ticker = "AAPL",
            companyName = "Apple Inc.",
            currentPrice = BigDecimal("150.000"),
            open = BigDecimal("148.000"),
            dayLow = BigDecimal("147.500"),
            dayHigh = BigDecimal("155.000"),
            fiftyTwoWeekHigh = BigDecimal("200.000"),
            updatedAt = Instant.now()
        )
        val event = MarketDataTickEvent(snapshot = snapshot, feedType = FeedType.REAL)

        listener.onMarketDataTick(event)

        verify(marketDataFeedService).handleMarketDataTick(event)
    }
})
