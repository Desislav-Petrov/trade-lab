package org.dpp.tradelab.stocktrading.messaging

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OrderRejectedEvent(
    val orderId: UUID,
    val accountId: UUID,
    val userId: UUID,
    val ticker: String,
    val quantity: BigDecimal,
    val rejectionReason: String,
    val timestamp: Instant
)
