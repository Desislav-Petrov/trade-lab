package org.dpp.tradelab.portfolio.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.portfolio.repository.PositionRepository
import org.dpp.tradelab.portfolio.repository.ProcessedIdempotencyKeyRepository
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.dpp.tradelab.stocktrading.messaging.OrderSide
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Integration test for [PortfolioPositionService] verifying transactional behavior
 * with a real database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PortfolioPositionServiceIntegrationTest(
    private val service: PortfolioPositionService,
    private val positionRepository: PositionRepository,
    private val processedIdempotencyKeyRepository: ProcessedIdempotencyKeyRepository
) : FunSpec({

    beforeTest {
        // Clean up before each test
        positionRepository.deleteAll()
        processedIdempotencyKeyRepository.deleteAll()
    }

    test("handleOrderFilled_transactionRollback_idempotencyKeyNotSavedOnFailure") {
        // Given - create an event with a ticker that's too long to violate the constraint
        // The ticker column has length = 10, so this should cause a database exception
        val event = OrderFilledEvent(
            orderId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            ticker = "VERYLONGTICKERSYMBOL", // 22 characters - exceeds 10 character limit
            quantity = BigDecimal("10"),
            executionPrice = BigDecimal("150.00"),
            idempotencyKey = UUID.randomUUID(),
            side = OrderSide.BUY,
            timestamp = Instant.now()
        )

        // When/Then - this should throw an exception due to the ticker constraint
        var exceptionThrown = false
        try {
            service.handleOrderFilled(event)
        } catch (e: Exception) {
            exceptionThrown = true
        }

        // Verify exception was thrown
        exceptionThrown shouldBe true

        // Verify that the idempotency key was NOT saved (transaction rolled back)
        val keyExists = processedIdempotencyKeyRepository.existsByIdempotencyKey(event.idempotencyKey)
        keyExists shouldBe false

        // Verify that no position was saved
        val positions = positionRepository.findAll()
        positions.size shouldBe 0
    }

    test("handleOrderFilled_integration_happyPath_savesPositionAndIdempotencyKey") {
        // Given
        val event = OrderFilledEvent(
            orderId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            ticker = "AAPL",
            quantity = BigDecimal("10"),
            executionPrice = BigDecimal("150.00"),
            idempotencyKey = UUID.randomUUID(),
            side = OrderSide.BUY,
            timestamp = Instant.now()
        )

        // When
        service.handleOrderFilled(event)

        // Then - verify idempotency key was saved
        val keyExists = processedIdempotencyKeyRepository.existsByIdempotencyKey(event.idempotencyKey)
        keyExists shouldBe true

        // Verify position was saved
        val positions = positionRepository.findByUserIdAndAccountIdAndTicker(
            event.userId,
            event.accountId,
            event.ticker
        )
        positions.isPresent shouldBe true

        val position = positions.get()
        position.quantity shouldBe BigDecimal("10.0000")
        position.totalCost shouldBe BigDecimal("1500.0000")
        position.avgPrice shouldBe BigDecimal("150.0000")
    }

    test("handleOrderFilled_integration_duplicateIdempotencyKey_noSecondWrite") {
        // Given
        val event = OrderFilledEvent(
            orderId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            ticker = "AAPL",
            quantity = BigDecimal("10"),
            executionPrice = BigDecimal("150.00"),
            idempotencyKey = UUID.randomUUID(),
            side = OrderSide.BUY,
            timestamp = Instant.now()
        )

        // Process the event the first time
        service.handleOrderFilled(event)

        // Get the position after first processing
        val positionAfterFirst = positionRepository.findByUserIdAndAccountIdAndTicker(
            event.userId,
            event.accountId,
            event.ticker
        ).get()

        // When - process the same event again (duplicate idempotency key)
        service.handleOrderFilled(event)

        // Then - position should not be modified
        val positionAfterSecond = positionRepository.findByUserIdAndAccountIdAndTicker(
            event.userId,
            event.accountId,
            event.ticker
        ).get()

        // Quantity should be the same (not doubled)
        positionAfterSecond.quantity shouldBe positionAfterFirst.quantity
        positionAfterSecond.totalCost shouldBe positionAfterFirst.totalCost
        positionAfterSecond.lastUpdated shouldBe positionAfterFirst.lastUpdated
    }
})
