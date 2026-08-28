package org.dpp.tradelab.portfolio.messaging

import org.dpp.tradelab.portfolio.service.PortfolioPositionService
import org.dpp.tradelab.portfolio.model.FillSide
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class StockTradingEventListener(
    private val portfolioPositionService: PortfolioPositionService
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOrderFilled(event: OrderFilledEvent) =
        portfolioPositionService.handleOrderFilled(
            orderId = event.orderId,
            accountId = event.accountId,
            userId = event.userId,
            ticker = event.ticker,
            quantity = event.quantity,
            side = FillSide.valueOf(event.side.name),
            executionPrice = event.executionPrice,
            timestamp = event.timestamp,
            idempotencyKey = event.idempotencyKey
        )
}
