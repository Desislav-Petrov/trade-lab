package org.dpp.tradelab.portfolio.messaging

import org.dpp.tradelab.portfolio.service.PortfolioPositionService
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Event listener for Stock Trading domain events.
 *
 * This listener reacts to events published by the Stock Trading domain and
 * delegates to the Portfolio domain services for processing.
 *
 * The listener uses [TransactionalEventListener] with [TransactionPhase.AFTER_COMMIT]
 * to ensure that events are only processed after the publishing transaction has
 * successfully committed.
 */
@Component
class StockTradingEventListener(
    private val portfolioPositionService: PortfolioPositionService
) {

    /**
     * Handles an [OrderFilledEvent] by delegating to the portfolio position service.
     *
     * This method is invoked after the Stock Trading domain's transaction has
     * successfully committed. The position service will create or update the
     * corresponding position in an idempotent, transactional manner.
     *
     * @param event The order-filled event to process
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOrderFilled(event: OrderFilledEvent) =
        portfolioPositionService.handleOrderFilled(event)
}
