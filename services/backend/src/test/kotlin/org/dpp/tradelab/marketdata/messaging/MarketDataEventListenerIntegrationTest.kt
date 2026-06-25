package org.dpp.tradelab.marketdata.messaging

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.marketdata.model.AssetSubscription
import org.dpp.tradelab.marketdata.repository.AssetSubscriptionRepository
import org.dpp.tradelab.marketdata.service.AssetSubscriptionService
import org.dpp.tradelab.marketdata.service.MarketDataFeedService
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

/**
 * Integration tests verifying that Spring Application Events emitted by [AssetSubscriptionService]
 * are correctly received and processed by [MarketDataFeedService]'s @EventListener methods.
 *
 * Uses the full Spring Boot context with H2 in-memory database so that event publication
 * and routing is exercised end-to-end.
 */
@SpringBootTest
class MarketDataEventListenerIntegrationTest(
    private val assetSubscriptionService: AssetSubscriptionService,
    private val marketDataFeedService: MarketDataFeedService,
    private val assetSubscriptionRepository: AssetSubscriptionRepository
) : FunSpec({

    extension(SpringExtension)

    val userId = UUID.randomUUID()

    afterEach {
        // Clean up subscriptions between tests to keep state isolated
        assetSubscriptionRepository.deleteAll()
        // Clear in-memory lookup maps
        marketDataFeedService.tickerToUsers.clear()
        marketDataFeedService.userToTickers.clear()
    }

    test("bulkAdd_emitsAssetSubscribedEvent_marketDataFeedService_updatesLookupMaps") {
        assetSubscriptionService.bulkAdd(userId, listOf("AAPL", "MSFT"))

        marketDataFeedService.tickerToUsers["AAPL"]?.contains(userId) shouldBe true
        marketDataFeedService.tickerToUsers["MSFT"]?.contains(userId) shouldBe true
        marketDataFeedService.userToTickers[userId]?.contains("AAPL") shouldBe true
        marketDataFeedService.userToTickers[userId]?.contains("MSFT") shouldBe true
    }

    test("bulkRemove_emitsAssetUnsubscribedEvent_marketDataFeedService_updatesLookupMaps") {
        // First subscribe
        assetSubscriptionService.bulkAdd(userId, listOf("AAPL", "GOOGL"))

        // Verify subscribed
        marketDataFeedService.tickerToUsers["AAPL"]?.contains(userId) shouldBe true
        marketDataFeedService.userToTickers[userId]?.contains("AAPL") shouldBe true

        // Now unsubscribe
        assetSubscriptionService.bulkRemove(userId, listOf("AAPL"))

        marketDataFeedService.tickerToUsers["AAPL"]?.contains(userId) shouldBe false
        // GOOGL should still be there
        marketDataFeedService.userToTickers[userId]?.contains("GOOGL") shouldBe true
    }
})
