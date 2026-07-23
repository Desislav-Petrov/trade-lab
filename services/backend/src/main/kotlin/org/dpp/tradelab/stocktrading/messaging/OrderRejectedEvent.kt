package org.dpp.tradelab.stocktrading.messaging

import org.dpp.tradelab.stocktrading.model.OrderSide
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OrderRejectedEvent(
    val orderId: UUID,
    val accountId: UUID,
    val userId: UUID,
    val ticker: String,
    val quantity: BigDecimal,
    val side: OrderSide,
    val rejectionReason: String,
    val timestamp: Instant
)
