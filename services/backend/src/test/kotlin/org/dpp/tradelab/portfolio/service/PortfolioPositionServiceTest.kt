package org.dpp.tradelab.portfolio.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.portfolio.model.AssetType
import org.dpp.tradelab.portfolio.model.Position
import org.dpp.tradelab.portfolio.model.ProcessedIdempotencyKey
import org.dpp.tradelab.portfolio.repository.PositionRepository
import org.dpp.tradelab.portfolio.repository.ProcessedIdempotencyKeyRepository
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.dpp.tradelab.stocktrading.messaging.OrderSide
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.Optional
import java.util.UUID

class PortfolioPositionServiceTest : FunSpec({

    lateinit var positionRepository: PositionRepository
    lateinit var processedIdempotencyKeyRepository: ProcessedIdempotencyKeyRepository
    lateinit var service: PortfolioPositionService

    beforeTest {
        positionRepository = mock()
        processedIdempotencyKeyRepository = mock()
        service = PortfolioPositionService(positionRepository, processedIdempotencyKeyRepository)
    }

    test("handleOrderFilled_duplicateIdempotencyKey_returnsImmediatelyWithoutWriting") {
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

        // Idempotency key already exists
        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(event.idempotencyKey))
            .thenReturn(true)

        // When
        service.handleOrderFilled(event)

        // Then
        verify(processedIdempotencyKeyRepository).existsByIdempotencyKey(event.idempotencyKey)
        verify(processedIdempotencyKeyRepository, never()).save(any<ProcessedIdempotencyKey>())
        verify(positionRepository, never()).findByUserIdAndAccountIdAndTicker(any(), any(), any())
        verify(positionRepository, never()).save(any<Position>())
    }

    test("handleOrderFilled_newPosition_createsPositionWithCorrectFields") {
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
            timestamp = Instant.parse("2026-07-09T10:00:00Z")
        )

        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(event.idempotencyKey))
            .thenReturn(false)
        whenever(positionRepository.findByUserIdAndAccountIdAndTicker(event.userId, event.accountId, event.ticker))
            .thenReturn(Optional.empty())

        // When
        service.handleOrderFilled(event)

        // Then
        // Verify idempotency key was saved
        val idempotencyKeyCaptor = argumentCaptor<ProcessedIdempotencyKey>()
        verify(processedIdempotencyKeyRepository).save(idempotencyKeyCaptor.capture())
        val savedIdempotencyKey = idempotencyKeyCaptor.firstValue
        savedIdempotencyKey.idempotencyKey shouldBe event.idempotencyKey
        savedIdempotencyKey.keyId shouldNotBe null

        // Verify position was created with correct values
        val positionCaptor = argumentCaptor<Position>()
        verify(positionRepository).save(positionCaptor.capture())
        val savedPosition = positionCaptor.firstValue

        savedPosition.positionId shouldNotBe null
        savedPosition.userId shouldBe event.userId
        savedPosition.accountId shouldBe event.accountId
        savedPosition.ticker shouldBe event.ticker
        savedPosition.assetType shouldBe AssetType.STOCK
        savedPosition.quantity shouldBe BigDecimal("10")
        savedPosition.totalCost shouldBe BigDecimal("1500.00")
        savedPosition.avgPrice shouldBe BigDecimal("150.00")
        savedPosition.minPrice shouldBe BigDecimal("150.00")
        savedPosition.maxPrice shouldBe BigDecimal("150.00")
        savedPosition.lastUpdated shouldBe event.timestamp
    }

    test("handleOrderFilled_existingPosition_updatesQuantityTotalCostAndAvgPrice") {
        // Given
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val ticker = "AAPL"

        val existingPosition = Position(
            positionId = UUID.randomUUID(),
            userId = userId,
            accountId = accountId,
            ticker = ticker,
            assetType = AssetType.STOCK,
            quantity = BigDecimal("10"),
            totalCost = BigDecimal("1500.00"),
            avgPrice = BigDecimal("150.00"),
            minPrice = BigDecimal("150.00"),
            maxPrice = BigDecimal("150.00"),
            lastUpdated = Instant.parse("2026-07-08T10:00:00Z")
        )

        val event = OrderFilledEvent(
            orderId = UUID.randomUUID(),
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = BigDecimal("5"),
            executionPrice = BigDecimal("160.00"),
            idempotencyKey = UUID.randomUUID(),
            side = OrderSide.BUY,
            timestamp = Instant.parse("2026-07-09T10:00:00Z")
        )

        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(event.idempotencyKey))
            .thenReturn(false)
        whenever(positionRepository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker))
            .thenReturn(Optional.of(existingPosition))

        // When
        service.handleOrderFilled(event)

        // Then
        // Verify idempotency key was saved
        verify(processedIdempotencyKeyRepository).save(any<ProcessedIdempotencyKey>())

        // Verify position was updated correctly
        verify(positionRepository).save(existingPosition)

        // quantity: 10 + 5 = 15
        existingPosition.quantity shouldBe BigDecimal("15")

        // totalCost: 1500.00 + (5 * 160.00) = 1500.00 + 800.00 = 2300.00
        existingPosition.totalCost shouldBe BigDecimal("2300.00")

        // avgPrice: 2300.00 / 15 = 153.3333...
        val expectedAvgPrice = BigDecimal("2300.00").divide(BigDecimal("15"), 4, RoundingMode.HALF_UP)
        existingPosition.avgPrice shouldBe expectedAvgPrice

        // minPrice: min(150.00, 160.00) = 150.00
        existingPosition.minPrice shouldBe BigDecimal("150.00")

        // maxPrice: max(150.00, 160.00) = 160.00
        existingPosition.maxPrice shouldBe BigDecimal("160.00")

        // lastUpdated: updated to event timestamp
        existingPosition.lastUpdated shouldBe event.timestamp
    }

    test("handleOrderFilled_existingPosition_updatesMinPrice") {
        // Given
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val ticker = "AAPL"

        val existingPosition = Position(
            positionId = UUID.randomUUID(),
            userId = userId,
            accountId = accountId,
            ticker = ticker,
            assetType = AssetType.STOCK,
            quantity = BigDecimal("10"),
            totalCost = BigDecimal("1500.00"),
            avgPrice = BigDecimal("150.00"),
            minPrice = BigDecimal("150.00"),
            maxPrice = BigDecimal("150.00"),
            lastUpdated = Instant.parse("2026-07-08T10:00:00Z")
        )

        val event = OrderFilledEvent(
            orderId = UUID.randomUUID(),
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = BigDecimal("5"),
            executionPrice = BigDecimal("140.00"), // Lower than existing minPrice
            idempotencyKey = UUID.randomUUID(),
            side = OrderSide.BUY,
            timestamp = Instant.parse("2026-07-09T10:00:00Z")
        )

        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(event.idempotencyKey))
            .thenReturn(false)
        whenever(positionRepository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker))
            .thenReturn(Optional.of(existingPosition))

        // When
        service.handleOrderFilled(event)

        // Then
        // minPrice should be updated to the new lower price
        existingPosition.minPrice shouldBe BigDecimal("140.00")
        // maxPrice should remain unchanged
        existingPosition.maxPrice shouldBe BigDecimal("150.00")
    }

    test("handleOrderFilled_existingPosition_updatesMaxPrice") {
        // Given
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val ticker = "AAPL"

        val existingPosition = Position(
            positionId = UUID.randomUUID(),
            userId = userId,
            accountId = accountId,
            ticker = ticker,
            assetType = AssetType.STOCK,
            quantity = BigDecimal("10"),
            totalCost = BigDecimal("1500.00"),
            avgPrice = BigDecimal("150.00"),
            minPrice = BigDecimal("150.00"),
            maxPrice = BigDecimal("150.00"),
            lastUpdated = Instant.parse("2026-07-08T10:00:00Z")
        )

        val event = OrderFilledEvent(
            orderId = UUID.randomUUID(),
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = BigDecimal("5"),
            executionPrice = BigDecimal("170.00"), // Higher than existing maxPrice
            idempotencyKey = UUID.randomUUID(),
            side = OrderSide.BUY,
            timestamp = Instant.parse("2026-07-09T10:00:00Z")
        )

        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(event.idempotencyKey))
            .thenReturn(false)
        whenever(positionRepository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker))
            .thenReturn(Optional.of(existingPosition))

        // When
        service.handleOrderFilled(event)

        // Then
        // maxPrice should be updated to the new higher price
        existingPosition.maxPrice shouldBe BigDecimal("170.00")
        // minPrice should remain unchanged
        existingPosition.minPrice shouldBe BigDecimal("150.00")
    }

    test("handleOrderFilled_existingPosition_allBigDecimalArithmetic") {
        // Given - test that all arithmetic is done with BigDecimal
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val ticker = "AAPL"

        // Create position with values that would cause floating-point precision issues
        val existingPosition = Position(
            positionId = UUID.randomUUID(),
            userId = userId,
            accountId = accountId,
            ticker = ticker,
            assetType = AssetType.STOCK,
            quantity = BigDecimal("3"),
            totalCost = BigDecimal("10.00"),
            avgPrice = BigDecimal("3.3333"),
            minPrice = BigDecimal("3.3333"),
            maxPrice = BigDecimal("3.3333"),
            lastUpdated = Instant.parse("2026-07-08T10:00:00Z")
        )

        val event = OrderFilledEvent(
            orderId = UUID.randomUUID(),
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = BigDecimal("7"),
            executionPrice = BigDecimal("3.3333"),
            idempotencyKey = UUID.randomUUID(),
            side = OrderSide.BUY,
            timestamp = Instant.parse("2026-07-09T10:00:00Z")
        )

        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(event.idempotencyKey))
            .thenReturn(false)
        whenever(positionRepository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker))
            .thenReturn(Optional.of(existingPosition))

        // When
        service.handleOrderFilled(event)

        // Then - verify BigDecimal precision is maintained
        existingPosition.quantity shouldBe BigDecimal("10")
        
        // totalCost: 10.00 + (7 * 3.3333) = 10.00 + 23.3331 = 33.3331
        existingPosition.totalCost shouldBe BigDecimal("33.3331")
        
        // avgPrice: 33.3331 / 10 = 3.3333 (rounded to 4 decimal places)
        existingPosition.avgPrice shouldBe BigDecimal("3.3333")
    }
})
