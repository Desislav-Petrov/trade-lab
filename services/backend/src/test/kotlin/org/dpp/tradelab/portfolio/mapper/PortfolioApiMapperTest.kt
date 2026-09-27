package org.dpp.tradelab.portfolio.mapper

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.portfolio.service.CashHoldingResult
import org.dpp.tradelab.portfolio.service.PortfolioHoldingsResult
import org.dpp.tradelab.portfolio.service.PortfolioInsights
import org.dpp.tradelab.portfolio.service.StockHoldingResult
import java.math.BigDecimal

class PortfolioApiMapperTest : FunSpec({
    val mapper = PortfolioApiMapper()

    test("toHoldingsView_mapsHoldingsAndCash_withoutInsights") {
        val result = PortfolioHoldingsResult(
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
                assetClassBreakdown = org.dpp.tradelab.portfolio.service.AssetClassBreakdown(
                    stockPercent = BigDecimal("37.5000"),
                    cashPercent = BigDecimal("62.5000"),
                    totalPortfolioValue = BigDecimal("800.0000")
                ),
                stockBreakdown = emptyList(),
                unrealisedPnLContribution = emptyList()
            )
        )

        val view = mapper.toHoldingsView(result)

        view.holdings shouldHaveSize 1
        view.holdings.single().ticker shouldBe "AAPL"
        view.holdings.single().quantity shouldBe BigDecimal("2.0000")
        view.holdings.single().currentPrice shouldBe BigDecimal("150.0000")
        view.holdings.single().currentValue shouldBe BigDecimal("300.0000")
        view.holdings.single().avgPrice shouldBe BigDecimal("140.0000")
        view.holdings.single().portfolioPercent shouldBe BigDecimal("37.5000")
        view.holdings.single().unrealisedPnL shouldBe BigDecimal("20.0000")
        view.cash.balance shouldBe BigDecimal("500.0000")
        view.cash.currency shouldBe "USD"
        view.cash.portfolioPercent shouldBe BigDecimal("62.5000")
    }
})
