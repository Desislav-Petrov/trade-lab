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
import java.math.RoundingMode
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

        if (event.side == OrderSide.BUY) {
            handleBuyFilled(event)
        } else {
            handleSellFilled(event)
        }
    }

    private fun handleBuyFilled(event: OrderFilledEvent) {
        // Look up existing position
        val existingPosition = positionRepository
            .findByUserIdAndAccountIdAndTicker(event.userId, event.accountId, event.ticker)

        if (existingPosition.isPresent) {
            // Update existing position
            val position = existingPosition.get()
            val fillCost = event.quantity.multiply(event.executionPrice)
            position.quantity = position.quantity.add(event.quantity)
            position.totalCost = position.totalCost.add(fillCost)
            position.avgPrice = position.totalCost.divide(position.quantity, 4, RoundingMode.HALF_UP)
            position.minPrice = position.minPrice.min(event.executionPrice)
            position.maxPrice = position.maxPrice.max(event.executionPrice)
            position.lastUpdated = event.timestamp
            // entity is already managed — dirty changes are flushed automatically on transaction commit
        } else {
            // Create new position
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
    }

    private fun handleSellFilled(event: OrderFilledEvent) {
        // Look up existing position by accountId and ticker
        val existingPosition = positionRepository
            .findByUserIdAndAccountIdAndTicker(event.userId, event.accountId, event.ticker)

        if (existingPosition.isPresent) {
            val position = existingPosition.get()
            val previousQuantity = position.quantity
            val remainingQuantity = if (previousQuantity.subtract(event.quantity).compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO
            } else {
                previousQuantity.subtract(event.quantity)
            }

            position.totalCost = if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO
            } else {
                position.totalCost.multiply(remainingQuantity).divide(previousQuantity, 4, RoundingMode.HALF_UP)
            }
            position.quantity = remainingQuantity
            position.avgPrice = if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                position.totalCost.divide(remainingQuantity, 4, RoundingMode.HALF_UP)
            } else {
                null
            }
            position.minPrice = position.minPrice.min(event.executionPrice)
            position.maxPrice = position.maxPrice.max(event.executionPrice)
            position.lastUpdated = event.timestamp
            // entity is already managed — dirty changes are flushed automatically on transaction commit
        }
        // If no position found, nothing to do (shouldn't happen if validation was correct)
    }
}
