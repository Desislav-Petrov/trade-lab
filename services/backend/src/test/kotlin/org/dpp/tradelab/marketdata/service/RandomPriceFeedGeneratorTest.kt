package org.dpp.tradelab.marketdata.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveAtMostSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
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

    val generator = RandomPriceFeedGenerator(supportedTickerConfig)

    beforeEach {
        whenever(supportedTickerConfig.getAll()).thenReturn(allTickers)
    }

    test("generateTick_resultSize_isBetween1And10Inclusive") {
        // Run multiple times to reduce the chance of a lucky pass with a bad implementation
        repeat(20) {
            val result = generator.generateTick()
            result shouldHaveAtLeastSize 1
            result shouldHaveAtMostSize 10
        }
    }

    test("generateTick_priceFields_areAllPositive") {
        repeat(10) {
            val result = generator.generateTick()
            result.forEach { snapshot ->
                snapshot.currentPrice shouldBeGreaterThan BigDecimal.ZERO
                snapshot.open shouldBeGreaterThan BigDecimal.ZERO
                snapshot.dayLow shouldBeGreaterThan BigDecimal.ZERO
                snapshot.fiftyTwoWeekHigh shouldBeGreaterThan BigDecimal.ZERO
            }
        }
    }

    test("generateTick_priceFields_haveScale3") {
        repeat(10) {
            val result = generator.generateTick()
            result.forEach { snapshot ->
                snapshot.currentPrice.scale() shouldBe 3
                snapshot.open.scale() shouldBe 3
                snapshot.dayLow.scale() shouldBe 3
                snapshot.fiftyTwoWeekHigh.scale() shouldBe 3
            }
        }
    }

    test("generateTick_updatedAt_isNonNull") {
        val result = generator.generateTick()
        result.forEach { snapshot ->
            snapshot.updatedAt shouldNotBe null
        }
    }

    test("generateTick_tickers_areUniqueWithinSingleCall") {
        repeat(20) {
            val result = generator.generateTick()
            result.map { it.ticker }.shouldBeUnique()
        }
    }

    test("generateTick_withSingleTicker_returnsExactlyOneTick") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))

        val result = generator.generateTick()

        result shouldHaveAtLeastSize 1
        result shouldHaveAtMostSize 1
    }

    test("generateTick_withEmptyTickers_returnsEmpty") {
        whenever(supportedTickerConfig.getAll()).thenReturn(emptyMap())

        val result = generator.generateTick()

        result.size shouldBe 0
    }
})
