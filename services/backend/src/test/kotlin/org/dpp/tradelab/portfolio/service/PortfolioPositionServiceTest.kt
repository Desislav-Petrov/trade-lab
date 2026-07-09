package org.dpp.tradelab.portfolio.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.portfolio.model.AssetType
import org.dpp.tradelab.portfolio.model.Position
import org.dpp.tradelab.portfolio.repository.PositionRepository
import org.dpp.tradelab.portfolio.repository.ProcessedIdempotencyKeyRepository
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.dpp.tradelab.stocktrading.messaging.OrderSide
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class PortfolioPositionServiceTest : FunSpec({

    val positionRepository = mock<PositionRepository>()
    val processedIdempotencyKeyRepository = mock<ProcessedIdempotencyKeyRepository>()

    val service = PortfolioPositionService(positionRepository, processedIdempotencyKeyRepository)

    val userId = UUID.randomUUID()
    val accountId = UUID.randomUUID()
    val ticker = "AAPL"
    val idempotencyKey = UUID.randomUUID()
    val executionPrice = BigDecimal("150.0000")
    val quantity = BigDecimal("2.0000")
    val timestamp = Instant.now()

    fun buildEvent(
        ik: UUID = idempotencyKey,
        qty: BigDecimal = quantity,
        price: BigDecimal = executionPrice
    ) = OrderFilledEvent(
        orderId = UUID.randomUUID(),
        accountId = accountId,
        userId = userId,
        ticker = ticker,
        quantity = qty,
        executionPrice = price,
        idempotencyKey = ik,
        side = OrderSide.BUY,
        timestamp = timestamp
    )

    beforeEach {
        org.mockito.kotlin.reset(positionRepository, processedIdempotencyKeyRepository)
        whenever(processedIdempotencyKeyRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(positionRepository.save(any())).thenAnswer { it.arguments[0] }
    }

    // ── Duplicate idempotency key ────────────────────────────────────────────

    test("handleOrderFilled_duplicateIdempotencyKey_returnsImmediatelyWithoutWriting") {
        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(true)

        service.handleOrderFilled(buildEvent())

        verify(processedIdempotencyKeyRepository, never()).save(any())
        verify(positionRepository, never()).findByUserIdAndAccountIdAndTicker(any(), any(), any())
        verify(positionRepository, never()).save(any())
    }

    // ── New position (no existing row) ───────────────────────────────────────

    test("handleOrderFilled_newPosition_createsPositionWithCorrectFields") {
        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(positionRepository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker))
            .thenReturn(Optional.empty())

        service.handleOrderFilled(buildEvent())

        val captor = argumentCaptor<Position>()
        verify(positionRepository).save(captor.capture())

        val saved = captor.firstValue
        saved.userId shouldBe userId
        saved.accountId shouldBe accountId
        saved.ticker shouldBe ticker
        saved.assetType shouldBe AssetType.STOCK
        saved.quantity shouldBe quantity
        saved.totalCost shouldBe quantity.multiply(executionPrice)
        saved.avgPrice shouldBe executionPrice
        saved.minPrice shouldBe executionPrice
        saved.maxPrice shouldBe executionPrice
        saved.lastUpdated shouldBe timestamp
    }

    // ── Existing position (row exists) ───────────────────────────────────────

    test("handleOrderFilled_existingPosition_updatesQuantityTotalCostAvgPriceMinMaxLastUpdated") {
        val existingQuantity = BigDecimal("3.0000")
        val existingAvgPrice = BigDecimal("140.0000")
        val existingTotalCost = existingQuantity.multiply(existingAvgPrice)
        val existingMin = BigDecimal("135.0000")
        val existingMax = BigDecimal("145.0000")

        val existingPosition = Position(
            positionId = UUID.randomUUID(),
            userId = userId,
            accountId = accountId,
            ticker = ticker,
            assetType = AssetType.STOCK,
            quantity = existingQuantity,
            totalCost = existingTotalCost,
            avgPrice = existingAvgPrice,
            minPrice = existingMin,
            maxPrice = existingMax,
            lastUpdated = Instant.now().minusSeconds(60)
        )

        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(positionRepository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker))
            .thenReturn(Optional.of(existingPosition))

        val newPrice = BigDecimal("150.0000")
        val newQty = BigDecimal("2.0000")
        service.handleOrderFilled(buildEvent(price = newPrice, qty = newQty))

        val expectedNewQuantity = existingQuantity.add(newQty) // 5.0000
        val expectedNewTotalCost = existingTotalCost.add(newQty.multiply(newPrice)) // 420 + 300 = 720
        val expectedNewAvgPrice = expectedNewTotalCost.divide(expectedNewQuantity, 4, java.math.RoundingMode.HALF_UP) // 144.0000

        existingPosition.quantity shouldBe expectedNewQuantity
        existingPosition.totalCost shouldBe expectedNewTotalCost
        existingPosition.avgPrice shouldBe expectedNewAvgPrice
        // new price 150 > existingMax 145 → maxPrice updated
        existingPosition.maxPrice shouldBe newPrice
        // new price 150 > existingMin 135 → minPrice not changed
        existingPosition.minPrice shouldBe existingMin
        existingPosition.lastUpdated shouldBe timestamp
    }

    test("handleOrderFilled_existingPosition_updatesMinPriceWhenNewPriceIsLower") {
        val existingPosition = Position(
            positionId = UUID.randomUUID(),
            userId = userId,
            accountId = accountId,
            ticker = ticker,
            assetType = AssetType.STOCK,
            quantity = BigDecimal("3.0000"),
            totalCost = BigDecimal("450.0000"),
            avgPrice = BigDecimal("150.0000"),
            minPrice = BigDecimal("140.0000"),
            maxPrice = BigDecimal("160.0000"),
            lastUpdated = Instant.now().minusSeconds(60)
        )

        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(positionRepository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker))
            .thenReturn(Optional.of(existingPosition))

        val lowerPrice = BigDecimal("130.0000")
        service.handleOrderFilled(buildEvent(price = lowerPrice))

        existingPosition.minPrice shouldBe lowerPrice
        existingPosition.maxPrice shouldBe BigDecimal("160.0000") // unchanged
    }

    // ── Idempotency key recorded ────────────────────────────────────────────

    test("handleOrderFilled_newEvent_savesIdempotencyKeyRecord") {
        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(positionRepository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker))
            .thenReturn(Optional.empty())

        service.handleOrderFilled(buildEvent())

        val captor = argumentCaptor<org.dpp.tradelab.portfolio.model.ProcessedIdempotencyKey>()
        verify(processedIdempotencyKeyRepository).save(captor.capture())

        captor.firstValue.idempotencyKey shouldBe idempotencyKey
    }
})
