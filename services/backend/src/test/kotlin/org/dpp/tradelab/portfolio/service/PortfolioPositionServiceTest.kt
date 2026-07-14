package org.dpp.tradelab.portfolio.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.portfolio.model.AssetType
import org.dpp.tradelab.portfolio.model.Position
import org.dpp.tradelab.portfolio.repository.PositionRepository
import org.dpp.tradelab.portfolio.repository.ProcessedIdempotencyKeyRepository
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.dpp.tradelab.stocktrading.model.OrderSide
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
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
        side: OrderSide = OrderSide.BUY,
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
        side = side,
        idempotencyKey = ik,
        timestamp = timestamp
    )

    fun buildPosition(
        quantity: BigDecimal,
        totalCost: BigDecimal,
        avgPrice: BigDecimal? = totalCost.divide(quantity),
        minPrice: BigDecimal = BigDecimal("140.0000"),
        maxPrice: BigDecimal = BigDecimal("160.0000")
    ) = Position(
        positionId = UUID.randomUUID(),
        userId = userId,
        accountId = accountId,
        ticker = ticker,
        assetType = AssetType.STOCK,
        quantity = quantity,
        totalCost = totalCost,
        avgPrice = avgPrice,
        minPrice = minPrice,
        maxPrice = maxPrice,
        lastUpdated = Instant.now().minusSeconds(60)
    )

    beforeEach {
        reset(positionRepository, processedIdempotencyKeyRepository)
        whenever(processedIdempotencyKeyRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(positionRepository.save(any())).thenAnswer { it.arguments[0] }
    }

    test("handleOrderFilled_duplicateIdempotencyKey_returnsImmediatelyWithoutWriting") {
        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(true)

        service.handleOrderFilled(buildEvent())

        verify(processedIdempotencyKeyRepository, never()).save(any())
        verify(positionRepository, never()).findByUserIdAndAccountIdAndTicker(any(), any(), any())
        verify(positionRepository, never()).save(any())
    }

    test("handleOrderFilled_buyPathExistingPosition_remainsUnchangedBehaviour") {
        val existingPosition = buildPosition(
            quantity = BigDecimal("3.0000"),
            totalCost = BigDecimal("420.0000"),
            avgPrice = BigDecimal("140.0000"),
            minPrice = BigDecimal("135.0000"),
            maxPrice = BigDecimal("145.0000")
        )

        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(positionRepository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker))
            .thenReturn(Optional.of(existingPosition))

        service.handleOrderFilled(buildEvent(side = OrderSide.BUY, qty = BigDecimal("2.0000"), price = BigDecimal("150.0000")))

        existingPosition.quantity.compareTo(BigDecimal("5.0000")) shouldBe 0
        existingPosition.totalCost.compareTo(BigDecimal("720.0000")) shouldBe 0
        existingPosition.avgPrice?.compareTo(BigDecimal("144.0000")) shouldBe 0
        existingPosition.minPrice shouldBe BigDecimal("135.0000")
        existingPosition.maxPrice shouldBe BigDecimal("150.0000")
        existingPosition.lastUpdated shouldBe timestamp
    }

    test("handleOrderFilled_sellPartial_decrementsQuantityAndCostProportionally") {
        val existingPosition = buildPosition(
            quantity = BigDecimal("10.0000"),
            totalCost = BigDecimal("1000.0000"),
            avgPrice = BigDecimal("100.0000"),
            minPrice = BigDecimal("90.0000"),
            maxPrice = BigDecimal("110.0000")
        )

        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(positionRepository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker))
            .thenReturn(Optional.of(existingPosition))

        service.handleOrderFilled(buildEvent(side = OrderSide.SELL, qty = BigDecimal("4.0000"), price = BigDecimal("120.0000")))

        existingPosition.quantity.compareTo(BigDecimal("6.0000")) shouldBe 0
        existingPosition.totalCost.compareTo(BigDecimal("600.0000")) shouldBe 0
        existingPosition.avgPrice?.compareTo(BigDecimal("100.0000")) shouldBe 0
        existingPosition.minPrice shouldBe BigDecimal("90.0000")
        existingPosition.maxPrice shouldBe BigDecimal("120.0000")
        existingPosition.lastUpdated shouldBe timestamp
    }

    test("handleOrderFilled_sellFullSellOut_retainsZeroQuantityRow") {
        val existingPosition = buildPosition(
            quantity = BigDecimal("4.0000"),
            totalCost = BigDecimal("400.0000"),
            avgPrice = BigDecimal("100.0000"),
            minPrice = BigDecimal("85.0000"),
            maxPrice = BigDecimal("105.0000")
        )

        whenever(processedIdempotencyKeyRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(positionRepository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker))
            .thenReturn(Optional.of(existingPosition))

        service.handleOrderFilled(buildEvent(side = OrderSide.SELL, qty = BigDecimal("4.0000"), price = BigDecimal("80.0000")))

        existingPosition.quantity.compareTo(BigDecimal.ZERO) shouldBe 0
        existingPosition.totalCost.compareTo(BigDecimal.ZERO) shouldBe 0
        existingPosition.avgPrice shouldBe null
        existingPosition.minPrice shouldBe BigDecimal("80.0000")
        existingPosition.maxPrice shouldBe BigDecimal("105.0000")
        existingPosition.lastUpdated shouldBe timestamp
    }

    test("handleOrderFilled_newBuyPosition_createsPositionWithCorrectFields") {
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
