package org.dpp.tradelab.portfolio.api

import java.math.BigDecimal

data class PortfolioHoldingsView(
    val holdings: List<StockHolding>,
    val cash: CashHolding
) {
    data class StockHolding(
        val ticker: String,
        val quantity: BigDecimal,
        val currentPrice: BigDecimal,
        val currentValue: BigDecimal,
        val minPrice: BigDecimal,
        val maxPrice: BigDecimal,
        val avgPrice: BigDecimal,
        val portfolioPercent: BigDecimal?,
        val unrealisedPnL: BigDecimal
    )

    data class CashHolding(
        val balance: BigDecimal,
        val currency: String,
        val portfolioPercent: BigDecimal?
    )
}
