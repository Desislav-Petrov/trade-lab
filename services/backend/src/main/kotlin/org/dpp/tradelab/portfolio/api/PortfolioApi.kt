package org.dpp.tradelab.portfolio.api

import java.math.BigDecimal
import java.util.UUID

interface PortfolioApi {
    fun getPositionQuantity(accountId: UUID, ticker: String): BigDecimal

    /**
     * Returns priced holdings for a single account owned by the given user.
     *
     * The [userId] is part of the authorization scope and must match the owner of [accountId].
     * Callers should expect portfolio-layer failures such as account not found / access denied
     * plus price or balance unavailable exceptions when upstream data cannot be loaded.
     */
    fun getHoldings(accountId: UUID, userId: UUID): PortfolioHoldingsView
}
