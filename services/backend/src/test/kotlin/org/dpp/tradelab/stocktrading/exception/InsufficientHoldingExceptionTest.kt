package org.dpp.tradelab.stocktrading.exception

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class InsufficientHoldingExceptionTest : FunSpec({
    test("insufficientHoldingException_carriesCorrectFieldValues") {
        val exception = InsufficientHoldingException(
            ticker = "AAPL",
            requested = BigDecimal("5.0000"),
            available = BigDecimal("2.0000")
        )

        exception.ticker shouldBe "AAPL"
        exception.requested shouldBe BigDecimal("5.0000")
        exception.available shouldBe BigDecimal("2.0000")
    }
})
