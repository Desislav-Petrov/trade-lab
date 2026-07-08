package org.dpp.tradelab.marketdata.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.marketdata.config.SupportedTickerConfig
import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.dpp.tradelab.marketdata.repository.AssetSubscriptionRepository
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class MarketDataFeedServiceApiTest : FunSpec({

    val repository = mock<AssetSubscriptionRepository>()
    val priceFeedGenerator = mock<PriceFeedGenerator>()
    val supportedTickerConfig = mock<SupportedTickerConfig>()

    fun buildService(): MarketDataFeedService {
        whenever(repository.findAll()).thenReturn(emptyList())
        whenever(priceFeedGenerator.generateTick()).thenReturn(emptyList())
        whenever(supportedTickerConfig.getAll()).thenReturn(emptyMap())
        return MarketDataFeedService(repository, priceFeedGenerator, supportedTickerConfig)
    }

    val aaplSnapshot = MarketDataSnapshot(
        ticker = "AAPL",
        companyName = "Apple Inc.",
        currentPrice = BigDecimal("182.500"),
        open = BigDecimal("180.000"),
        dayLow = BigDecimal("179.000"),
        fiftyTwoWeekHigh = BigDecimal("200.000"),
        updatedAt = Instant.now()
    )

    // ── getCurrentPrice tests ─────────────────────────────────────────────────

    test("getCurrentPrice_knownTicker_returnsCurrentPrice") {
        val service = buildService()
        service.snapshotCache["AAPL"] = aaplSnapshot

        val price = service.getCurrentPrice("AAPL")

        price shouldBe BigDecimal("182.500")
    }

    test("getCurrentPrice_knownTickerLowercase_returnsCurrentPrice") {
        val service = buildService()
        service.snapshotCache["AAPL"] = aaplSnapshot

        val price = service.getCurrentPrice("aapl")

        price shouldBe BigDecimal("182.500")
    }

    test("getCurrentPrice_unknownTicker_throwsIllegalStateException") {
        val service = buildService()

        shouldThrow<IllegalStateException> {
            service.getCurrentPrice("UNKNOWN")
        }
    }

    // ── isTickerSupported tests ───────────────────────────────────────────────

    test("isTickerSupported_knownTicker_returnsTrue") {
        whenever(supportedTickerConfig.resolve("AAPL")).thenReturn("Apple Inc.")
        val service = buildService()

        service.isTickerSupported("AAPL") shouldBe true
    }

    test("isTickerSupported_unknownTicker_returnsFalse") {
        whenever(supportedTickerConfig.resolve("UNKNOWN")).thenReturn(null)
        val service = buildService()

        service.isTickerSupported("UNKNOWN") shouldBe false
    }
})
