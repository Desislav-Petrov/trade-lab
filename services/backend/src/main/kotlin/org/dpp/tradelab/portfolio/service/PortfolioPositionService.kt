package org.dpp.tradelab.portfolio.service

import org.dpp.tradelab.portfolio.model.AssetType
import org.dpp.tradelab.portfolio.model.Position
import org.dpp.tradelab.portfolio.model.ProcessedIdempotencyKey
import org.dpp.tradelab.portfolio.repository.PositionRepository
import org.dpp.tradelab.portfolio.repository.ProcessedIdempotencyKeyRepository
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.dpp.tradelab.stocktrading.model.OrderSide
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class PortfolioPositionService(
    private val positionRepository: PositionRepository,
    private val processedIdempotencyKeyRepository: ProcessedIdempotencyKeyRepository
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleOrderFilled(event: OrderFilledEvent) {
        // Step 1: Check idempotency — if already processed, discard silently
        if (processedIdempotencyKeyRepository.existsByIdempotencyKey(event.idempotencyKey)) {
            return
        }

        // Step 2: Record idempotency key in same transaction
        val idempotencyRecord = ProcessedIdempotencyKey(
            keyId = UUID.randomUUID(),
            idempotencyKey = event.idempotencyKey,
            processedAt = Instant.now()
        )
        processedIdempotencyKeyRepository.save(idempotencyRecord)

        // Step 3: Look up existing position
        val existingPosition = positionRepository
            .findByUserIdAndAccountIdAndTicker(event.userId, event.accountId, event.ticker)

        when (event.side) {
            OrderSide.BUY -> handleBuy(event, existingPosition.orElse(null))
            OrderSide.SELL -> handleSell(event, existingPosition.orElse(null))
        }
    }

    private fun handleBuy(event: OrderFilledEvent, existingPosition: Position?) {
        if (existingPosition != null) {
            val fillCost = event.quantity.multiply(event.executionPrice)
            existingPosition.quantity = existingPosition.quantity.add(event.quantity)
            existingPosition.totalCost = existingPosition.totalCost.add(fillCost)
            existingPosition.avgPrice = existingPosition.totalCost.divide(
                existingPosition.quantity,
                4,
                java.math.RoundingMode.HALF_UP
            )
            existingPosition.minPrice = existingPosition.minPrice.min(event.executionPrice)
            existingPosition.maxPrice = existingPosition.maxPrice.max(event.executionPrice)
            existingPosition.lastUpdated = event.timestamp
            return
        }

        val fillCost = event.quantity.multiply(event.executionPrice)
        val newPosition = Position(
            positionId = UUID.randomUUID(),
            userId = event.userId,
            accountId = event.accountId,
            ticker = event.ticker,
            assetType = AssetType.STOCK,
            quantity = event.quantity,
            totalCost = fillCost,
            avgPrice = event.executionPrice,
            minPrice = event.executionPrice,
            maxPrice = event.executionPrice,
            lastUpdated = event.timestamp
        )
        positionRepository.save(newPosition)
    }

    private fun handleSell(event: OrderFilledEvent, existingPosition: Position?) {
        val position = requireNotNull(existingPosition) {
            "No existing position found for sell event ${event.orderId}"
        }
        val previousQuantity = position.quantity
        val remainingQuantity = previousQuantity.subtract(event.quantity)
        val remainingTotalCost = if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO
        } else {
            position.totalCost.multiply(remainingQuantity).divide(previousQuantity)
        }

        position.quantity = remainingQuantity
        position.totalCost = remainingTotalCost
        position.avgPrice = if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            null
        } else {
            remainingTotalCost.divide(remainingQuantity)
        }
        position.minPrice = position.minPrice.min(event.executionPrice)
        position.maxPrice = position.maxPrice.max(event.executionPrice)
        position.lastUpdated = event.timestamp
    }
}
