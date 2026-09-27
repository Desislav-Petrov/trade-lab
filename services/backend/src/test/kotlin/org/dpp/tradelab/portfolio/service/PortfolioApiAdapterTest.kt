package org.dpp.tradelab.portfolio.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.portfolio.exception.PortfolioPriceUnavailableException
import org.dpp.tradelab.portfolio.mapper.PortfolioApiMapper
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class PortfolioApiAdapterTest : FunSpec({
    val portfolioQueryService = mock<PortfolioQueryService>()
    val adapter = PortfolioApiAdapter(portfolioQueryService, PortfolioApiMapper())
    val accountId = UUID.randomUUID()
    val userId = UUID.randomUUID()

    test("getHoldings_delegatesToQueryService_andMapsToApiView") {
        whenever(portfolioQueryService.getHoldings(accountId, userId)).thenReturn(
            PortfolioHoldingsResult(
                holdings = listOf(
                    StockHoldingResult(
                        ticker = "AAPL",
                        quantity = BigDecimal("2.0000"),
                        currentPrice = BigDecimal("150.0000"),
                        currentValue = BigDecimal("300.0000"),
                        minPrice = BigDecimal("120.0000"),
                        maxPrice = BigDecimal("170.0000"),
                        avgPrice = BigDecimal("140.0000"),
                        portfolioPercent = BigDecimal("37.5000"),
                        unrealisedPnL = BigDecimal("20.0000")
                    )
                ),
                cash = CashHoldingResult(
                    balance = BigDecimal("500.0000"),
                    currency = "USD",
                    portfolioPercent = BigDecimal("62.5000")
                ),
                insights = PortfolioInsights(
                    assetClassBreakdown = AssetClassBreakdown(
                        stockPercent = BigDecimal("37.5000"),
                        cashPercent = BigDecimal("62.5000"),
                        totalPortfolioValue = BigDecimal("800.0000")
                    ),
                    stockBreakdown = emptyList(),
                    unrealisedPnLContribution = emptyList()
                )
            )
        )

        val view = adapter.getHoldings(accountId, userId)

        verify(portfolioQueryService).getHoldings(accountId, userId)
        view.holdings.single().ticker shouldBe "AAPL"
        view.holdings.single().avgPrice shouldBe BigDecimal("140.0000")
        view.holdings.single().unrealisedPnL shouldBe BigDecimal("20.0000")
        view.cash.balance shouldBe BigDecimal("500.0000")
        view.cash.currency shouldBe "USD"
    }

    test("getHoldings_queryServiceThrows_exceptionPropagatesUnchanged") {
        val exception = PortfolioPriceUnavailableException("Price data unavailable")
        whenever(portfolioQueryService.getHoldings(accountId, userId)).thenThrow(exception)

        val thrown = shouldThrow<PortfolioPriceUnavailableException> {
            adapter.getHoldings(accountId, userId)
        }

        thrown shouldBe exception
    }

    test("getPositionQuantity_delegatesToQueryService") {
        whenever(portfolioQueryService.getPositionQuantity(accountId, "AAPL")).thenReturn(BigDecimal("3.0000"))

        val quantity = adapter.getPositionQuantity(accountId, "AAPL")

        quantity shouldBe BigDecimal("3.0000")
        verify(portfolioQueryService).getPositionQuantity(accountId, "AAPL")
    }
})
