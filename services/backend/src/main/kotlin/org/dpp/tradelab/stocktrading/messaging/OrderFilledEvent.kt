package org.dpp.tradelab.stocktrading.messaging

import org.dpp.tradelab.stocktrading.model.OrderSide
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OrderFilledEvent(
    val orderId: UUID,
    val accountId: UUID,
    val userId: UUID,
    val ticker: String,
    val quantity: BigDecimal,
    val executionPrice: BigDecimal,
    val idempotencyKey: UUID,
    val side: OrderSide,
    val timestamp: Instant
)
