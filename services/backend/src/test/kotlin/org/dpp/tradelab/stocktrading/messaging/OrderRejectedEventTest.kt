package org.dpp.tradelab.stocktrading.messaging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.stocktrading.model.OrderSide
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class OrderRejectedEventTest : FunSpec({

    test("orderRejectedEvent_constructedWithAllFields_hasCorrectValues") {
        val orderId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val ticker = "AAPL"
        val quantity = BigDecimal("2.0000")
        val timestamp = Instant.now()

        val event = OrderRejectedEvent(
            orderId = orderId,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            side = OrderSide.SELL,
            rejectionReason = "Quantity exceeds holding",
            timestamp = timestamp
        )

        event.orderId shouldBe orderId
        event.accountId shouldBe accountId
        event.userId shouldBe userId
        event.ticker shouldBe ticker
        event.quantity shouldBe quantity
        event.side shouldBe OrderSide.SELL
        event.rejectionReason shouldBe "Quantity exceeds holding"
        event.timestamp shouldBe timestamp
    }
})
