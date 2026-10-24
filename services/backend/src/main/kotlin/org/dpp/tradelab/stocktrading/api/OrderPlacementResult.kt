package org.dpp.tradelab.stocktrading.api

import java.math.BigDecimal
import java.util.UUID

data class OrderPlacementResult(
    val orderId: UUID,
    val status: OrderPlacementStatus,
    val ticker: String,
    val quantity: BigDecimal,
    val side: OrderPlacementSide,
    val executionPrice: BigDecimal? = null,
    val totalAmount: BigDecimal? = null,
    val rejectionReason: String? = null
)
