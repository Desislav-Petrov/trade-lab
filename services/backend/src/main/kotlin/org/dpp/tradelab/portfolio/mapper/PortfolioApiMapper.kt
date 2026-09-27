package org.dpp.tradelab.portfolio.mapper

import org.dpp.tradelab.portfolio.api.PortfolioHoldingsView
import org.dpp.tradelab.portfolio.service.PortfolioHoldingsResult
import org.springframework.stereotype.Component

@Component
class PortfolioApiMapper {
    fun toHoldingsView(result: PortfolioHoldingsResult): PortfolioHoldingsView =
        PortfolioHoldingsView(
            holdings = result.holdings.map { holding ->
                PortfolioHoldingsView.StockHolding(
                    ticker = holding.ticker,
                    quantity = holding.quantity,
                    currentPrice = holding.currentPrice,
                    currentValue = holding.currentValue,
                    minPrice = holding.minPrice,
                    maxPrice = holding.maxPrice,
                    avgPrice = holding.avgPrice,
                    portfolioPercent = holding.portfolioPercent,
                    unrealisedPnL = holding.unrealisedPnL
                )
            },
            cash = PortfolioHoldingsView.CashHolding(
                balance = result.cash.balance,
                currency = result.cash.currency,
                portfolioPercent = result.cash.portfolioPercent
            )
        )
}
