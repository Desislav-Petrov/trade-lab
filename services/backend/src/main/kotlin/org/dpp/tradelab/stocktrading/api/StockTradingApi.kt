package org.dpp.tradelab.stocktrading.api

import java.math.BigDecimal
import java.util.UUID

interface StockTradingApi {
    fun placeMarketOrder(
        idempotencyKey: UUID,
        accountId: UUID,
        userId: UUID,
        ticker: String,
        quantity: BigDecimal,
        side: OrderPlacementSide
    ): OrderPlacementResult
}
