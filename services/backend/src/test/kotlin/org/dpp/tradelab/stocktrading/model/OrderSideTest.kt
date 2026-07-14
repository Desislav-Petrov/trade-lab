package org.dpp.tradelab.stocktrading.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OrderSideTest : FunSpec({

    test("orderSide_hasBuyAndSellValues") {
        OrderSide.BUY.name shouldBe "BUY"
        OrderSide.SELL.name shouldBe "SELL"
    }

    test("orderSide_canConstructOrderWithBuySide") {
        val order = Order(
            orderId = java.util.UUID.randomUUID(),
            idempotencyKey = java.util.UUID.randomUUID(),
            accountId = java.util.UUID.randomUUID(),
            userId = java.util.UUID.randomUUID(),
            ticker = "AAPL",
            quantity = java.math.BigDecimal("1.0000"),
            orderType = OrderType.MARKET,
            side = OrderSide.BUY,
            status = OrderStatus.PENDING,
            priceSnapshot = java.math.BigDecimal("150.000")
        )
        order.side shouldBe OrderSide.BUY
    }

    test("orderSide_canConstructOrderWithSellSide") {
        val order = Order(
            orderId = java.util.UUID.randomUUID(),
            idempotencyKey = java.util.UUID.randomUUID(),
            accountId = java.util.UUID.randomUUID(),
            userId = java.util.UUID.randomUUID(),
            ticker = "AAPL",
            quantity = java.math.BigDecimal("1.0000"),
            orderType = OrderType.MARKET,
            side = OrderSide.SELL,
            status = OrderStatus.PENDING,
            priceSnapshot = java.math.BigDecimal("150.000")
        )
        order.side shouldBe OrderSide.SELL
    }
})
