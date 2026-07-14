package org.dpp.tradelab.stocktrading.exception

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal

class InsufficientHoldingExceptionTest : FunSpec({

    test("insufficientHoldingException_fieldsAreSetCorrectly") {
        val ticker = "AAPL"
        val requested = BigDecimal("5.0000")
        val available = BigDecimal("2.0000")

        val ex = InsufficientHoldingException(ticker, requested, available)

        ex.ticker shouldBe ticker
        ex.requested shouldBe requested
        ex.available shouldBe available
        ex.message shouldContain ticker
        ex.message shouldContain requested.toPlainString()
        ex.message shouldContain available.toPlainString()
    }
})
