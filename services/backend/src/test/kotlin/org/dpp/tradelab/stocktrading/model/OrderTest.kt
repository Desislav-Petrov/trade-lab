package org.dpp.tradelab.stocktrading.model

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.util.UUID

class OrderTest : DescribeSpec({

    val orderId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val idempotencyKey = UUID.fromString("22222222-2222-2222-2222-222222222222")
    val accountId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    val userId = UUID.fromString("44444444-4444-4444-4444-444444444444")

    fun buildOrder(side: OrderSide) = Order(
        orderId = orderId,
        idempotencyKey = idempotencyKey,
        accountId = accountId,
        userId = userId,
        ticker = "AAPL",
        quantity = BigDecimal("2.5000"),
        side = side,
        orderType = OrderType.MARKET,
        status = OrderStatus.PENDING,
        priceSnapshot = BigDecimal("182.5000")
    )

    describe("Order construction") {
        it("isNew returns true on fresh instance") {
            buildOrder(OrderSide.BUY).isNew() shouldBe true
        }

        it("getId returns orderId") {
            buildOrder(OrderSide.BUY).id shouldBe orderId
        }

        it("can be constructed with both buy and sell sides") {
            buildOrder(OrderSide.BUY).side shouldBe OrderSide.BUY
            buildOrder(OrderSide.SELL).side shouldBe OrderSide.SELL
        }

        it("allows transitioning status and execution fields") {
            val order = buildOrder(OrderSide.BUY)
            order.status = OrderStatus.FILLED
            order.executionPrice = BigDecimal("185.0000")

            order.status shouldBe OrderStatus.FILLED
            order.executionPrice shouldBe BigDecimal("185.0000")
            order.rejectionReason shouldBe null
        }
    }

    describe("Order equality and string representation") {
        it("two instances with the same orderId are equal") {
            val left = buildOrder(OrderSide.BUY)
            val right = Order(
                orderId = orderId,
                idempotencyKey = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                ticker = "MSFT",
                quantity = BigDecimal("5.0000"),
                side = OrderSide.SELL,
                orderType = OrderType.MARKET,
                status = OrderStatus.FILLED,
                priceSnapshot = BigDecimal("100.0000")
            )

            (left == right) shouldBe true
            left.hashCode() shouldBe orderId.hashCode()
        }

        it("toString contains orderId and ticker") {
            val order = buildOrder(OrderSide.SELL)

            order.toString() shouldNotBe null
            order.toString().contains(orderId.toString()) shouldBe true
            order.toString().contains("AAPL") shouldBe true
        }
    }
})
