package org.dpp.tradelab.stocktrading.repository

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.stocktrading.model.Order
import org.dpp.tradelab.stocktrading.model.OrderSide
import org.dpp.tradelab.stocktrading.model.OrderStatus
import org.dpp.tradelab.stocktrading.model.OrderType
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
@AutoConfigureTestEntityManager
@Transactional
class OrderRepositoryTest(
    private val repository: OrderRepository,
    private val em: TestEntityManager
) : DescribeSpec({

    extension(SpringExtension)

    fun buildOrder(idempotencyKey: UUID = UUID.randomUUID()): Order = Order(
        orderId = UUID.randomUUID(),
        idempotencyKey = idempotencyKey,
        accountId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        ticker = "AAPL",
        quantity = BigDecimal("2.0000"),
        side = OrderSide.BUY,
        orderType = OrderType.MARKET,
        status = OrderStatus.PENDING,
        priceSnapshot = BigDecimal("182.500")
    )

    describe("OrderRepository.existsByIdempotencyKey") {

        it("existsByIdempotencyKey_knownKey_returnsTrue") {
            val idempotencyKey = UUID.randomUUID()
            val order = buildOrder(idempotencyKey)
            em.persistAndFlush(order)
            em.clear()

            repository.existsByIdempotencyKey(idempotencyKey) shouldBe true
        }

        it("existsByIdempotencyKey_unknownKey_returnsFalse") {
            repository.existsByIdempotencyKey(UUID.randomUUID()) shouldBe false
        }
    }

    describe("OrderRepository idempotency key unique constraint") {

        it("savingTwoOrdersWithSameIdempotencyKey_throwsConstraintViolation") {
            val idempotencyKey = UUID.randomUUID()
            val order1 = buildOrder(idempotencyKey)
            em.persistAndFlush(order1)

            val order2 = buildOrder(idempotencyKey)
            shouldThrow<Exception> {
                em.persistAndFlush(order2)
            }
        }

        it("savingTwoOrdersWithDifferentIdempotencyKeys_succeeds") {
            val order1 = buildOrder()
            val order2 = buildOrder()
            em.persistAndFlush(order1)
            em.persistAndFlush(order2)

            repository.existsByIdempotencyKey(order1.idempotencyKey) shouldBe true
            repository.existsByIdempotencyKey(order2.idempotencyKey) shouldBe true
        }
    }
})
