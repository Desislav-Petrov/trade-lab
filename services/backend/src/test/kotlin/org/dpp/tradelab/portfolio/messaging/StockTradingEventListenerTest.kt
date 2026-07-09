package org.dpp.tradelab.portfolio.messaging

import io.kotest.core.spec.style.FunSpec
import org.dpp.tradelab.portfolio.service.PortfolioPositionService
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.dpp.tradelab.stocktrading.messaging.OrderSide
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Unit test for [StockTradingEventListener].
 *
 * This test verifies that the listener correctly delegates to the
 * [PortfolioPositionService] when events are received.
 */
class StockTradingEventListenerTest : FunSpec({

    test("onOrderFilled_callsHandleOrderFilledWithExactEventPayload") {
        // Given
        val mockService = mock<PortfolioPositionService>()
        val listener = StockTradingEventListener(mockService)

        val event = OrderFilledEvent(
            orderId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            ticker = "AAPL",
            quantity = BigDecimal("10.0000"),
            executionPrice = BigDecimal("150.2500"),
            idempotencyKey = UUID.randomUUID(),
            side = OrderSide.BUY,
            timestamp = Instant.parse("2026-07-09T12:00:00Z")
        )

        // When
        listener.onOrderFilled(event)

        // Then
        verify(mockService).handleOrderFilled(event)
    }
})
