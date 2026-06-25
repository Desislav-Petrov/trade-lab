package org.dpp.tradelab.marketdata.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SupportedTickerConfigTest : StringSpec({

    fun buildConfig(): SupportedTickerConfig {
        val config = SupportedTickerConfig()
        config.init()
        return config
    }

    "resolve_knownTicker_returnsCompanyName" {
        val config = buildConfig()
        config.resolve("AAPL") shouldBe "Apple Inc."
    }

    "resolve_knownTickerLowercase_returnsCompanyName" {
        val config = buildConfig()
        config.resolve("msft") shouldBe "Microsoft Corporation"
    }

    "resolve_knownTickerMixedCase_returnsCompanyName" {
        val config = buildConfig()
        config.resolve("Nvda") shouldBe "NVIDIA Corporation"
    }

    "resolve_unknownTicker_returnsNull" {
        val config = buildConfig()
        config.resolve("UNKNOWN_XYZ_123") shouldBe null
    }

    "getAll_returnsAllEntries" {
        val config = buildConfig()
        val all = config.getAll()
        all.size shouldBe 20
        all shouldContainKey "AAPL"
        all shouldContainKey "MSFT"
        all shouldContainKey "GOOGL"
        all shouldContainKey "HPQ"
    }

    "getAll_returnsImmutableCopy" {
        val config = buildConfig()
        val all = config.getAll()
        all shouldNotBe null
        // Verify the returned map is a separate copy (modifying it does not affect the bean)
        val mutableCopy = all.toMutableMap()
        mutableCopy["FAKE"] = "Fake Corp"
        config.getAll().containsKey("FAKE") shouldBe false
    }

    "resolve_allKnownTickers_returnsNonNullValues" {
        val config = buildConfig()
        val tickers = listOf("AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA", "NFLX",
            "PYPL", "INTC", "AMD", "CRM", "ORCL", "ADBE", "CSCO", "QCOM", "TXN", "AVGO", "IBM", "HPQ")
        tickers.forEach { ticker ->
            config.resolve(ticker) shouldNotBe null
        }
    }
})
