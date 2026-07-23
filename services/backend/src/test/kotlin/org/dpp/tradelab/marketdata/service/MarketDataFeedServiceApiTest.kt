package org.dpp.tradelab.marketdata.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.marketdata.config.SupportedTickerConfig
import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.dpp.tradelab.marketdata.repository.AssetSubscriptionRepository
import org.dpp.tradelab.user.api.UserSettingsApi
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class MarketDataFeedServiceApiTest : FunSpec({

    val repository = mock<AssetSubscriptionRepository>()
    val priceFeedGenerator = mock<PriceFeedGenerator>()
    val supportedTickerConfig = mock<SupportedTickerConfig>()
    val userSettingsApi = mock<UserSettingsApi>()

    fun buildService(): MarketDataFeedService {
        whenever(repository.findAll()).thenReturn(emptyList())
        whenever(priceFeedGenerator.generateTick()).thenReturn(emptyList())
        whenever(supportedTickerConfig.getAll()).thenReturn(emptyMap())
        whenever(userSettingsApi.getAllUserSettings()).thenReturn(emptyList())
        return MarketDataFeedService(repository, priceFeedGenerator, supportedTickerConfig, userSettingsApi)
    }

    val aaplSnapshot = MarketDataSnapshot(
        ticker = "AAPL",
        companyName = "Apple Inc.",
        currentPrice = BigDecimal("182.500"),
        open = BigDecimal("180.000"),
        dayLow = BigDecimal("179.000"),
        dayHigh = BigDecimal("185.000"),
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

    // ── getPrices tests ───────────────────────────────────────────────────────

    test("getPrices_allTickersPresentInCache_returnsCorrectPrices") {
        val service = buildService()
        service.snapshotCache["AAPL"] = aaplSnapshot
        val msftSnapshot = MarketDataSnapshot(
            ticker = "MSFT",
            companyName = "Microsoft Corporation",
            currentPrice = BigDecimal("340.750"),
            open = BigDecimal("338.000"),
            dayLow = BigDecimal("337.000"),
            dayHigh = BigDecimal("342.000"),
            fiftyTwoWeekHigh = BigDecimal("350.000"),
            updatedAt = Instant.now()
        )
        service.snapshotCache["MSFT"] = msftSnapshot

        val result = service.getPrices(listOf("AAPL", "MSFT"))

        result shouldBe mapOf(
            "AAPL" to BigDecimal("182.500"),
            "MSFT" to BigDecimal("340.750")
        )
    }

    test("getPrices_someTickersMissingFromCache_onlyPresentTickersReturned") {
        val service = buildService()
        service.snapshotCache["AAPL"] = aaplSnapshot

        val result = service.getPrices(listOf("AAPL", "UNKNOWN", "MISSING"))

        result shouldBe mapOf("AAPL" to BigDecimal("182.500"))
    }

    test("getPrices_emptyTickerList_returnsEmptyMap") {
        val service = buildService()

        val result = service.getPrices(emptyList())

        result shouldBe emptyMap()
    }

    test("getPrices_lowercaseTickers_normalizesToUppercase") {
        val service = buildService()
        service.snapshotCache["AAPL"] = aaplSnapshot

        val result = service.getPrices(listOf("aapl"))

        result shouldBe mapOf("AAPL" to BigDecimal("182.500"))
    }

    // ── getPrice tests ────────────────────────────────────────────────────────

    test("getPrice_supportedTickerInCache_returnsCurrentPrice") {
        val service = buildService()
        service.snapshotCache["AAPL"] = aaplSnapshot
        whenever(supportedTickerConfig.resolve("AAPL")).thenReturn("Apple Inc.")

        val price = service.getPrice("AAPL")

        price shouldBe BigDecimal("182.500")
    }

    test("getPrice_unsupportedTicker_throwsUnsupportedTickerException") {
        val service = buildService()
        whenever(supportedTickerConfig.resolve("UNKNOWN")).thenReturn(null)

        shouldThrow<org.dpp.tradelab.marketdata.exception.UnsupportedTickerException> {
            service.getPrice("UNKNOWN")
        }
    }
})
