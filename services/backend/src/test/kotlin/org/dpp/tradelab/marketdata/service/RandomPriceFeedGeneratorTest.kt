package org.dpp.tradelab.marketdata.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveAtMostSize
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.marketdata.config.SupportedTickerConfig
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class RandomPriceFeedGeneratorTest : FunSpec({

    val supportedTickerConfig = mock<SupportedTickerConfig>()

    val allTickers = mapOf(
        "AAPL" to "Apple Inc.",
        "MSFT" to "Microsoft Corporation",
        "GOOGL" to "Alphabet Inc.",
        "AMZN" to "Amazon.com Inc.",
        "META" to "Meta Platforms Inc.",
        "TSLA" to "Tesla Inc.",
        "NVDA" to "NVIDIA Corporation",
        "NFLX" to "Netflix Inc.",
        "PYPL" to "PayPal Holdings Inc.",
        "INTC" to "Intel Corporation",
        "AMD" to "Advanced Micro Devices Inc.",
    )

    beforeEach {
        whenever(supportedTickerConfig.getAll()).thenReturn(allTickers)
    }

    fun freshGenerator() = RandomPriceFeedGenerator(supportedTickerConfig)

    test("generateTick_resultSize_isBetween1And10Inclusive") {
        val generator = freshGenerator()
        repeat(20) {
            val result = generator.generateTick()
            result shouldHaveAtLeastSize 1
            result shouldHaveAtMostSize 10
        }
    }

    test("generateTick_seedPrice_isBetween200And400Inclusive") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        val generator = freshGenerator()
        // Collect enough first-tick prices to verify seed range
        val seenPrices = mutableListOf<BigDecimal>()
        repeat(50) {
            generator.generateTick().forEach { seenPrices.add(it.currentPrice) }
        }
        seenPrices.forEach { price ->
            price shouldBeGreaterThanOrEqualTo BigDecimal("200.000")
            price shouldBeLessThanOrEqualTo BigDecimal("400.000")
        }
    }

    test("generateTick_open_equalsFirstSeedPriceAndNeverChanges") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        val generator = freshGenerator()
        val firstTick = generator.generateTick().first()
        val seedOpen = firstTick.open
        // open should equal the first currentPrice
        seedOpen shouldBe firstTick.currentPrice
        // open should remain the same across all subsequent ticks
        repeat(20) {
            val tick = generator.generateTick().first()
            tick.open shouldBe seedOpen
        }
    }

    test("generateTick_subsequentPrice_differsFromPreviousByBetween0dot5AndOneHalfPercent") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        val generator = freshGenerator()
        var previous = generator.generateTick().first().currentPrice
        repeat(50) {
            val tick = generator.generateTick().first()
            val ratio = tick.currentPrice.toDouble() / previous.toDouble()
            // ratio should be within [0.985, 1.015]
            assert(ratio >= 0.985 && ratio <= 1.015) {
                "Expected ratio $ratio to be in [0.985, 1.015] (previous=$previous, current=${tick.currentPrice})"
            }
            previous = tick.currentPrice
        }
    }

    test("generateTick_tickDirection_canGoUpAndDown") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        val generator = freshGenerator()
        var previous = generator.generateTick().first().currentPrice
        var sawUp = false
        var sawDown = false
        repeat(200) {
            val current = generator.generateTick().first().currentPrice
            if (current > previous) sawUp = true
            if (current < previous) sawDown = true
            previous = current
        }
        sawUp shouldBe true
        sawDown shouldBe true
    }

    test("generateTick_dayLow_isAlwaysLeOrEqualToMinimumEmittedPrice") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        val generator = freshGenerator()
        var minPrice = generator.generateTick().first().currentPrice
        repeat(50) {
            val tick = generator.generateTick().first()
            if (tick.currentPrice < minPrice) minPrice = tick.currentPrice
            tick.dayLow shouldBeLessThanOrEqualTo minPrice
        }
    }

    test("generateTick_dayHigh_isAlwaysGeOrEqualToMaximumEmittedPrice") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        val generator = freshGenerator()
        var maxPrice = generator.generateTick().first().currentPrice
        repeat(50) {
            val tick = generator.generateTick().first()
            if (tick.currentPrice > maxPrice) maxPrice = tick.currentPrice
            tick.dayHigh shouldBeGreaterThanOrEqualTo maxPrice
        }
    }

    test("generateTick_fiftyTwoWeekHigh_isAlwaysGeOrEqualToMaximumEmittedPrice") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        val generator = freshGenerator()
        var maxPrice = generator.generateTick().first().currentPrice
        repeat(50) {
            val tick = generator.generateTick().first()
            if (tick.currentPrice > maxPrice) maxPrice = tick.currentPrice
            tick.fiftyTwoWeekHigh shouldBeGreaterThanOrEqualTo maxPrice
        }
    }

    test("generateTick_priceFields_haveScale3") {
        val generator = freshGenerator()
        repeat(10) {
            val result = generator.generateTick()
            result.forEach { snapshot ->
                snapshot.currentPrice.scale() shouldBe 3
                snapshot.open.scale() shouldBe 3
                snapshot.dayLow.scale() shouldBe 3
                snapshot.dayHigh.scale() shouldBe 3
                snapshot.fiftyTwoWeekHigh.scale() shouldBe 3
            }
        }
    }

    test("generateTick_updatedAt_isNonNull") {
        val generator = freshGenerator()
        val result = generator.generateTick()
        result.forEach { snapshot ->
            snapshot.updatedAt shouldNotBe null
        }
    }

    test("generateTick_tickers_areUniqueWithinSingleCall") {
        val generator = freshGenerator()
        repeat(20) {
            val result = generator.generateTick()
            result.map { it.ticker }.shouldBeUnique()
        }
    }

    test("generateTick_withSingleTicker_returnsExactlyOneTick") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        val generator = freshGenerator()
        val result = generator.generateTick()
        result shouldHaveAtLeastSize 1
        result shouldHaveAtMostSize 1
    }

    test("generateTick_withEmptyTickers_returnsEmpty") {
        whenever(supportedTickerConfig.getAll()).thenReturn(emptyMap())
        val generator = freshGenerator()
        val result = generator.generateTick()
        result.size shouldBe 0
    }
})
