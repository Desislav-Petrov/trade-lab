package org.dpp.tradelab.stocktrading.messaging

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
    val timestamp: Instant
)
