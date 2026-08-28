package org.dpp.tradelab.portfolio.service

import org.dpp.tradelab.portfolio.model.AssetType
import org.dpp.tradelab.portfolio.model.FillSide
import org.dpp.tradelab.portfolio.model.Position
import org.dpp.tradelab.portfolio.model.PositionFill
import org.dpp.tradelab.portfolio.model.ProcessedIdempotencyKey
import org.dpp.tradelab.portfolio.repository.PositionFillRepository
import org.dpp.tradelab.portfolio.repository.PositionRepository
import org.dpp.tradelab.portfolio.repository.ProcessedIdempotencyKeyRepository
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
    private val processedIdempotencyKeyRepository: ProcessedIdempotencyKeyRepository,
    private val positionFillRepository: PositionFillRepository
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleOrderFilled(
        orderId: UUID,
        accountId: UUID,
        userId: UUID,
        ticker: String,
        quantity: BigDecimal,
        side: FillSide,
        executionPrice: BigDecimal,
        timestamp: Instant,
        idempotencyKey: UUID
    ) {
        // Step 1: Check idempotency — if already processed, discard silently
        if (processedIdempotencyKeyRepository.existsByIdempotencyKey(idempotencyKey)) {
            return
        }

        // Step 2: Record idempotency key in same transaction
        val idempotencyRecord = ProcessedIdempotencyKey(
            keyId = UUID.randomUUID(),
            idempotencyKey = idempotencyKey,
            processedAt = Instant.now()
        )
        processedIdempotencyKeyRepository.save(idempotencyRecord)

        val positionFill = PositionFill(
            id = UUID.randomUUID(),
            userId = userId,
            accountId = accountId,
            ticker = ticker,
            assetType = AssetType.STOCK,
            side = side,
            executionPrice = executionPrice,
            quantity = quantity,
            filledAt = timestamp,
            idempotencyKey = idempotencyKey
        )
        positionFillRepository.save(positionFill)

        if (side == FillSide.BUY) {
            handleBuyFilled(accountId, userId, ticker, quantity, executionPrice, timestamp)
        } else {
            handleSellFilled(accountId, userId, ticker, quantity, executionPrice, timestamp)
        }
    }

    private fun handleBuyFilled(
        accountId: UUID,
        userId: UUID,
        ticker: String,
        quantity: BigDecimal,
        executionPrice: BigDecimal,
        timestamp: Instant
    ) {
        // Look up existing position
        val existingPosition = positionRepository
            .findByUserIdAndAccountIdAndTicker(userId, accountId, ticker)

        if (existingPosition.isPresent) {
            // Update existing position
            val position = existingPosition.get()
            val fillCost = quantity.multiply(executionPrice)
            position.quantity = position.quantity.add(quantity)
            position.totalCost = position.totalCost.add(fillCost)
            position.avgPrice = position.totalCost.divide(position.quantity, 4, RoundingMode.HALF_UP)
            position.minPrice = position.minPrice.min(executionPrice)
            position.maxPrice = position.maxPrice.max(executionPrice)
            position.lastUpdated = timestamp
            // entity is already managed — dirty changes are flushed automatically on transaction commit
        } else {
            // Create new position
            val fillCost = quantity.multiply(executionPrice)
            val newPosition = Position(
                positionId = UUID.randomUUID(),
                userId = userId,
                accountId = accountId,
                ticker = ticker,
                assetType = AssetType.STOCK,
                quantity = quantity,
                totalCost = fillCost,
                avgPrice = executionPrice,
                minPrice = executionPrice,
                maxPrice = executionPrice,
                lastUpdated = timestamp
            )
            positionRepository.save(newPosition)
        }
    }

    private fun handleSellFilled(
        accountId: UUID,
        userId: UUID,
        ticker: String,
        quantity: BigDecimal,
        executionPrice: BigDecimal,
        timestamp: Instant
    ) {
        // Look up existing position by accountId and ticker
        val existingPosition = positionRepository
            .findByUserIdAndAccountIdAndTicker(userId, accountId, ticker)

        if (existingPosition.isPresent) {
            val position = existingPosition.get()
            val previousQuantity = position.quantity
            val remainingQuantity = if (previousQuantity.subtract(quantity).compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO
            } else {
                previousQuantity.subtract(quantity)
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
            position.minPrice = position.minPrice.min(executionPrice)
            position.maxPrice = position.maxPrice.max(executionPrice)
            position.lastUpdated = timestamp
            // entity is already managed — dirty changes are flushed automatically on transaction commit
        }
        // If no position found, nothing to do (shouldn't happen if validation was correct)
    }
}
