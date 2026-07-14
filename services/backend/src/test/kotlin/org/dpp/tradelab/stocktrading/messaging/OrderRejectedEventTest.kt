package org.dpp.tradelab.stocktrading.messaging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.stocktrading.model.OrderSide
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class OrderRejectedEventTest : FunSpec({

    test("orderRejectedEvent_constructedWithBuySide_hasCorrectSide") {
        val event = OrderRejectedEvent(
            orderId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            ticker = "AAPL",
            quantity = BigDecimal("2.0000"),
            rejectionReason = "Insufficient funds",
            side = OrderSide.BUY,
            timestamp = Instant.now()
        )

        event.side shouldBe OrderSide.BUY
        event.rejectionReason shouldBe "Insufficient funds"
    }

    test("orderRejectedEvent_constructedWithSellSide_hasCorrectSide") {
        val event = OrderRejectedEvent(
            orderId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            ticker = "MSFT",
            quantity = BigDecimal("1.0000"),
            rejectionReason = "Quantity exceeds holding",
            side = OrderSide.SELL,
            timestamp = Instant.now()
        )

        event.side shouldBe OrderSide.SELL
        event.rejectionReason shouldBe "Quantity exceeds holding"
    }
})
