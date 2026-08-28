package org.dpp.tradelab.portfolio.controller

import org.dpp.tradelab.portfolio.generated.api.PortfolioApiDelegate
import org.dpp.tradelab.portfolio.generated.model.AssetClassBreakdown
import org.dpp.tradelab.portfolio.generated.model.CashHolding
import org.dpp.tradelab.portfolio.generated.model.FillDataPoint
import org.dpp.tradelab.portfolio.generated.model.FillHistoryEntry
import org.dpp.tradelab.portfolio.generated.model.FillHistoryResponse
import org.dpp.tradelab.portfolio.generated.model.PortfolioHoldingsResponse
import org.dpp.tradelab.portfolio.generated.model.PortfolioInsights
import org.dpp.tradelab.portfolio.generated.model.StockBreakdownEntry
import org.dpp.tradelab.portfolio.generated.model.StockHolding
import org.dpp.tradelab.portfolio.generated.model.UnrealisedPnLEntry
import org.dpp.tradelab.portfolio.service.PortfolioQueryService
import org.dpp.tradelab.user.exception.InvalidTokenException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.time.ZoneOffset
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

        val insights = PortfolioInsights(
            assetClassBreakdown = AssetClassBreakdown(
                stockPercent = result.insights.assetClassBreakdown.stockPercent,
                cashPercent = result.insights.assetClassBreakdown.cashPercent,
                totalPortfolioValue = result.insights.assetClassBreakdown.totalPortfolioValue
            ),
            stockBreakdown = result.insights.stockBreakdown.map { entry ->
                StockBreakdownEntry(
                    ticker = entry.ticker,
                    currentValue = entry.currentValue,
                    percentOfStockPortfolio = entry.percentOfStockPortfolio
                )
            },
            unrealisedPnLContribution = result.insights.unrealisedPnLContribution.map { entry ->
                UnrealisedPnLEntry(
                    ticker = entry.ticker,
                    unrealisedPnL = entry.unrealisedPnL
                )
            }
        )

        return ResponseEntity.ok(PortfolioHoldingsResponse(holdings = holdings, cash = cash, insights = insights))
    }

    override fun getFills(accountId: UUID, page: Int, size: Int): ResponseEntity<FillHistoryResponse> {
        require(size <= 100) { "size must be less than or equal to 100" }

        val userId = SecurityContextHolder.getContext().authentication?.principal as? UUID
            ?: throw InvalidTokenException("Authentication required")
        val result = portfolioQueryService.getFillHistory(userId, accountId, page, size)

        val fills = result.fills.map { (ticker, fillList) ->
            FillHistoryEntry(
                ticker = ticker,
                dataPoints = fillList.map { fill ->
                    FillDataPoint(
                        filledAt = fill.filledAt.atOffset(ZoneOffset.UTC),
                        executionPrice = fill.executionPrice,
                        quantity = fill.quantity,
                        side = FillDataPoint.Side.valueOf(fill.side.name)
                    )
                }
            )
        }

        return ResponseEntity.ok(
            FillHistoryResponse(
                page = result.page,
                propertySize = result.size,
                totalPages = result.totalPages,
                totalElements = result.totalElements.toInt(),
                fills = fills
            )
        )
    }
}
