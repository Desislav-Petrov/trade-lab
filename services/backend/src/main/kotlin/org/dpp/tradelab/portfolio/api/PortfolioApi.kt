package org.dpp.tradelab.portfolio.api

import java.math.BigDecimal
import java.util.UUID

interface PortfolioApi {
    fun getPositionQuantity(accountId: UUID, ticker: String): BigDecimal
}
