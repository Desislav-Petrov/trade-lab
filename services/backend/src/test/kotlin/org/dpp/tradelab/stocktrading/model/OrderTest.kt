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

    describe("Order construction") {

        it("isNew returns true on fresh instance") {
            val order = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )

            order.isNew() shouldBe true
        }

        it("getId returns orderId (Persistable contract)") {
            val order = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )

            order.id shouldBe orderId
        }

        it("stores all required fields correctly") {
            val order = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.5000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )

            order.orderId shouldBe orderId
            order.idempotencyKey shouldBe idempotencyKey
            order.accountId shouldBe accountId
            order.userId shouldBe userId
            order.ticker shouldBe "AAPL"
            order.quantity shouldBe BigDecimal("2.5000")
            order.orderType shouldBe OrderType.MARKET
            order.status shouldBe OrderStatus.PENDING
            order.priceSnapshot shouldBe BigDecimal("182.500")
        }

        it("executionPrice defaults to null") {
            val order = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )

            order.executionPrice shouldBe null
        }

        it("rejectionReason defaults to null") {
            val order = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )

            order.rejectionReason shouldBe null
        }

        it("allows setting executionPrice after construction") {
            val order = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )

            order.executionPrice = BigDecimal("185.000")
            order.executionPrice shouldBe BigDecimal("185.000")
        }

        it("allows transitioning status to FILLED") {
            val order = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )

            order.status = OrderStatus.FILLED
            order.status shouldBe OrderStatus.FILLED
        }

        it("allows transitioning status to REJECTED") {
            val order = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )

            order.status = OrderStatus.REJECTED
            order.rejectionReason = "Insufficient funds"
            order.status shouldBe OrderStatus.REJECTED
            order.rejectionReason shouldBe "Insufficient funds"
        }
    }

    describe("Order equals and hashCode") {

        it("two instances with the same orderId are equal") {
            val a = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )
            val b = Order(
                orderId = orderId,
                idempotencyKey = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                ticker = "GOOG",
                quantity = BigDecimal("5.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.FILLED,
                priceSnapshot = BigDecimal("100.000")
            )

            (a == b) shouldBe true
        }

        it("two instances with different orderIds are not equal") {
            val a = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )
            val b = Order(
                orderId = UUID.randomUUID(),
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )

            (a == b) shouldBe false
        }

        it("hashCode is based on orderId") {
            val order = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )

            order.hashCode() shouldBe orderId.hashCode()
        }
    }

    describe("Order toString") {

        it("toString contains orderId") {
            val order = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )

            order.toString() shouldNotBe null
            order.toString().contains(orderId.toString()) shouldBe true
        }

        it("toString contains ticker") {
            val order = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                orderType = OrderType.MARKET,
                side = OrderSide.BUY,
                status = OrderStatus.PENDING,
                priceSnapshot = BigDecimal("182.500")
            )

            order.toString().contains("AAPL") shouldBe true
        }
    }
})
