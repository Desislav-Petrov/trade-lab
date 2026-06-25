package org.dpp.tradelab.marketdata.controller

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

/**
 * Verifies that the Spring Boot application context loads successfully
 * with the [MarketDataWebSocketConfig] present.
 *
 * This test is intentionally minimal — it only asserts that the context starts
 * without an error, which proves that bean wiring and configuration is correct.
 */
@SpringBootTest
class MarketDataWebSocketConfigTest(
    private val applicationContext: ApplicationContext,
    private val marketDataWebSocketConfig: MarketDataWebSocketConfig
) : FunSpec({

    extension(SpringExtension)

    test("applicationContext_loadsSuccessfully") {
        applicationContext shouldNotBe null
    }

    test("marketDataWebSocketConfig_beanIsPresent") {
        marketDataWebSocketConfig shouldNotBe null
    }
})
