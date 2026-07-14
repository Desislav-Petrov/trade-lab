package org.dpp.tradelab.stocktrading.messaging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.stocktrading.model.OrderSide
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class OrderFilledEventTest : FunSpec({

    test("orderFilledEvent_constructedWithAllFields_hasCorrectValues") {
        val orderId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val idempotencyKey = UUID.randomUUID()
        val ticker = "AAPL"
        val quantity = BigDecimal("2.0000")
        val executionPrice = BigDecimal("182.5000")
        val timestamp = Instant.now()

        val event = OrderFilledEvent(
            orderId = orderId,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            executionPrice = executionPrice,
            idempotencyKey = idempotencyKey,
            side = OrderSide.BUY,
            timestamp = timestamp
        )

        event.orderId shouldBe orderId
        event.accountId shouldBe accountId
        event.userId shouldBe userId
        event.ticker shouldBe ticker
        event.quantity shouldBe quantity
        event.executionPrice shouldBe executionPrice
        event.idempotencyKey shouldBe idempotencyKey
        event.side shouldBe OrderSide.BUY
        event.timestamp shouldBe timestamp
    }

    test("orderFilledEvent_sellSide_hasSellSide") {
        val event = OrderFilledEvent(
            orderId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            ticker = "MSFT",
            quantity = BigDecimal("1.0000"),
            executionPrice = BigDecimal("300.0000"),
            idempotencyKey = UUID.randomUUID(),
            side = OrderSide.SELL,
            timestamp = Instant.now()
        )

        event.side shouldBe OrderSide.SELL
    }
})
