package org.dpp.tradelab.portfolio.messaging

import io.kotest.core.spec.style.FunSpec
import org.dpp.tradelab.portfolio.model.FillSide
import org.dpp.tradelab.portfolio.service.PortfolioPositionService
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.dpp.tradelab.stocktrading.model.OrderSide
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class StockTradingEventListenerTest : FunSpec({

    val portfolioPositionService = mock<PortfolioPositionService>()
    val listener = StockTradingEventListener(portfolioPositionService)

    test("onOrderFilled_callsHandleOrderFilledWithExactEventPayload") {
        val event = OrderFilledEvent(
            orderId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            ticker = "AAPL",
            quantity = BigDecimal("5.0000"),
            executionPrice = BigDecimal("180.0000"),
            idempotencyKey = UUID.randomUUID(),
            side = OrderSide.BUY,
            timestamp = Instant.now()
        )

        listener.onOrderFilled(event)

        verify(portfolioPositionService).handleOrderFilled(
            orderId = event.orderId,
            accountId = event.accountId,
            userId = event.userId,
            ticker = event.ticker,
            quantity = event.quantity,
            side = FillSide.BUY,
            executionPrice = event.executionPrice,
            timestamp = event.timestamp,
            idempotencyKey = event.idempotencyKey
        )
        verifyNoMoreInteractions(portfolioPositionService)
    }
})
