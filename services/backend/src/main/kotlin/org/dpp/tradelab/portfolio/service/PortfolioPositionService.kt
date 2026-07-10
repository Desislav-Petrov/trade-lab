package org.dpp.tradelab.portfolio.service

import org.dpp.tradelab.portfolio.model.AssetType
import org.dpp.tradelab.portfolio.model.Position
import org.dpp.tradelab.portfolio.model.ProcessedIdempotencyKey
import org.dpp.tradelab.portfolio.repository.PositionRepository
import org.dpp.tradelab.portfolio.repository.ProcessedIdempotencyKeyRepository
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class PortfolioPositionService(
    private val positionRepository: PositionRepository,
    private val processedIdempotencyKeyRepository: ProcessedIdempotencyKeyRepository
) {

    @Transactional
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

        if (existingPosition.isPresent) {
            // Step 4a: Update existing position
            val position = existingPosition.get()
            val fillCost = event.quantity.multiply(event.executionPrice)
            position.quantity = position.quantity.add(event.quantity)
            position.totalCost = position.totalCost.add(fillCost)
            position.avgPrice = position.totalCost.divide(position.quantity, 4, java.math.RoundingMode.HALF_UP)
            position.minPrice = position.minPrice.min(event.executionPrice)
            position.maxPrice = position.maxPrice.max(event.executionPrice)
            position.lastUpdated = event.timestamp
            // entity is already managed — dirty changes are flushed automatically on transaction commit
        } else {
            // Step 4b: Create new position
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
}
