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

/**
 * Service responsible for maintaining portfolio position aggregates in response
 * to order-filled events from the Stock Trading domain.
 *
 * All position updates are idempotent and transactional: the service checks for
 * duplicate event keys before writing, and inserts the idempotency record and
 * position update atomically in a single transaction.
 */
@Service
class PortfolioPositionService(
    private val positionRepository: PositionRepository,
    private val processedIdempotencyKeyRepository: ProcessedIdempotencyKeyRepository
) {

    /**
     * Handles an [OrderFilledEvent] by creating or updating the corresponding
     * [Position] row for the filled order.
     *
     * This method is idempotent: if the event's [OrderFilledEvent.idempotencyKey]
     * has already been processed, the method returns immediately without making
     * any changes.
     *
     * For new positions (no existing row for userId + accountId + ticker):
     * - Creates a new [Position] with quantity, totalCost, avgPrice, minPrice,
     *   maxPrice, and lastUpdated based on the event.
     *
     * For existing positions:
     * - Increments quantity and totalCost
     * - Recalculates avgPrice as totalCost / quantity
     * - Updates minPrice and maxPrice if the executionPrice is lower or higher
     * - Updates lastUpdated
     *
     * The idempotency key is recorded in the same transaction as the position write.
     * If any part of the transaction fails, both writes are rolled back.
     *
     * @param event The order-filled event to process
     */
    @Transactional
    fun handleOrderFilled(event: OrderFilledEvent) {
        // Step 1: Check idempotency — if already processed, return immediately
        if (processedIdempotencyKeyRepository.existsByIdempotencyKey(event.idempotencyKey)) {
            return
        }

        // Step 2: Record the idempotency key within the same transaction
        val processedKey = ProcessedIdempotencyKey(
            keyId = UUID.randomUUID(),
            idempotencyKey = event.idempotencyKey,
            processedAt = Instant.now()
        )
        processedIdempotencyKeyRepository.save(processedKey)

        // Step 3: Look up existing position
        val existingPosition = positionRepository.findByUserIdAndAccountIdAndTicker(
            userId = event.userId,
            accountId = event.accountId,
            ticker = event.ticker
        )

        if (existingPosition.isEmpty) {
            // Step 4a: Create new position
            val newPosition = Position(
                positionId = UUID.randomUUID(),
                userId = event.userId,
                accountId = event.accountId,
                ticker = event.ticker,
                assetType = AssetType.STOCK, // Currently only STOCK is supported
                quantity = event.quantity,
                totalCost = event.quantity.multiply(event.executionPrice),
                avgPrice = event.executionPrice,
                minPrice = event.executionPrice,
                maxPrice = event.executionPrice,
                lastUpdated = event.timestamp
            )
            positionRepository.save(newPosition)
        } else {
            // Step 4b: Update existing position
            val position = existingPosition.get()
            
            // Increment quantity and totalCost
            position.quantity = position.quantity.add(event.quantity)
            position.totalCost = position.totalCost.add(event.quantity.multiply(event.executionPrice))
            
            // Recalculate average price
            position.avgPrice = position.totalCost.divide(
                position.quantity,
                4,
                java.math.RoundingMode.HALF_UP
            )
            
            // Update min and max prices
            position.minPrice = position.minPrice.min(event.executionPrice)
            position.maxPrice = position.maxPrice.max(event.executionPrice)
            
            // Update last updated timestamp
            position.lastUpdated = event.timestamp
            
            positionRepository.save(position)
        }
    }
}
