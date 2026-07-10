package org.dpp.tradelab.portfolio.controller

import org.dpp.tradelab.portfolio.generated.api.PortfolioApiDelegate
import org.dpp.tradelab.portfolio.generated.model.CashHolding
import org.dpp.tradelab.portfolio.generated.model.PortfolioHoldingsResponse
import org.dpp.tradelab.portfolio.generated.model.StockHolding
import org.dpp.tradelab.portfolio.service.PortfolioQueryService
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PortfolioApiDelegateImpl(
    private val portfolioQueryService: PortfolioQueryService
) : PortfolioApiDelegate {

    override fun getHoldings(accountId: UUID, userId: UUID): ResponseEntity<PortfolioHoldingsResponse> {
        val result = portfolioQueryService.getHoldings(accountId, userId)

        val holdings = result.holdings.map { h ->
            StockHolding(
                ticker = h.ticker,
                quantity = h.quantity,
                currentPrice = h.currentPrice,
                currentValue = h.currentValue,
                minPrice = h.minPrice,
                maxPrice = h.maxPrice,
                avgPrice = h.avgPrice,
                portfolioPercent = h.portfolioPercent,
                unrealisedPnL = h.unrealisedPnL
            )
        }

        val cash = CashHolding(
            balance = result.cash.balance,
            currency = result.cash.currency,
            portfolioPercent = result.cash.portfolioPercent
        )

        return ResponseEntity.ok(PortfolioHoldingsResponse(holdings = holdings, cash = cash))
    }
}
