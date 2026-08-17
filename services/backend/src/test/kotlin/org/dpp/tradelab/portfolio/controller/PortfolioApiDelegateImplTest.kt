package org.dpp.tradelab.portfolio.controller

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.dpp.tradelab.portfolio.exception.PortfolioAccountAccessDeniedException
import org.dpp.tradelab.portfolio.exception.PortfolioAccountNotFoundException
import org.dpp.tradelab.portfolio.exception.PortfolioBalanceUnavailableException
import org.dpp.tradelab.portfolio.exception.PortfolioPriceUnavailableException
import org.dpp.tradelab.portfolio.service.AssetClassBreakdown
import org.dpp.tradelab.portfolio.service.CashHoldingResult
import org.dpp.tradelab.portfolio.service.PortfolioHoldingsResult
import org.dpp.tradelab.portfolio.service.PortfolioInsights
import org.dpp.tradelab.portfolio.service.PortfolioQueryService
import org.dpp.tradelab.portfolio.service.StockBreakdownEntry
import org.dpp.tradelab.portfolio.service.StockHoldingResult
import org.dpp.tradelab.portfolio.service.UnrealisedPnLEntry
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID


@SpringBootTest
@AutoConfigureMockMvc
class PortfolioApiDelegateImplTest(
    @Autowired val mockMvc: MockMvc,
    @MockitoBean val portfolioQueryService: PortfolioQueryService
) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        fun buildHoldingsResult() = PortfolioHoldingsResult(
            holdings = listOf(
                StockHoldingResult(
                    ticker = "AAPL",
                    quantity = BigDecimal("2.0000"),
                    currentPrice = BigDecimal("150.0000"),
                    currentValue = BigDecimal("300.0000"),
                    minPrice = BigDecimal("140.0000"),
                    maxPrice = BigDecimal("160.0000"),
                    avgPrice = BigDecimal("145.0000"),
                    portfolioPercent = BigDecimal("37.5000"),
                    unrealisedPnL = BigDecimal("10.0000")
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
                stockBreakdown = listOf(
                    StockBreakdownEntry(
                        ticker = "AAPL",
                        currentValue = BigDecimal("300.0000"),
                        percentOfStockPortfolio = BigDecimal("100.0000")
                    )
                ),
                unrealisedPnLContribution = listOf(
                    UnrealisedPnLEntry(
                        ticker = "AAPL",
                        unrealisedPnL = BigDecimal("10.0000")
                    )
                )
            )
        )

        fun buildEmptyStockHoldingsResult() = PortfolioHoldingsResult(
            holdings = emptyList(),
            cash = CashHoldingResult(
                balance = BigDecimal("500.0000"),
                currency = "USD",
                portfolioPercent = BigDecimal("100.0000")
            ),
            insights = PortfolioInsights(
                assetClassBreakdown = AssetClassBreakdown(
                    stockPercent = BigDecimal.ZERO,
                    cashPercent = BigDecimal("100.0000"),
                    totalPortfolioValue = BigDecimal("500.0000")
                ),
                stockBreakdown = emptyList(),
                unrealisedPnLContribution = emptyList()
            )
        )

        fun buildZeroTotalValueResult() = PortfolioHoldingsResult(
            holdings = emptyList(),
            cash = CashHoldingResult(
                balance = BigDecimal.ZERO,
                currency = "USD",
                portfolioPercent = null
            ),
            insights = PortfolioInsights(
                assetClassBreakdown = AssetClassBreakdown(
                    stockPercent = null,
                    cashPercent = null,
                    totalPortfolioValue = BigDecimal.ZERO
                ),
                stockBreakdown = emptyList(),
                unrealisedPnLContribution = emptyList()
            )
        )

        test("getHoldings_happyPath_returns200WithCorrectBody") {
            whenever(portfolioQueryService.getHoldings(any(), any())).thenReturn(buildHoldingsResult())

            mockMvc.perform(
                get("/api/v1/portfolio/holdings")
                    .param("accountId", accountId.toString())
                    .param("userId", userId.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("\$.holdings").isArray)
                .andExpect(jsonPath("\$.holdings[0].ticker").value("AAPL"))
                .andExpect(jsonPath("\$.holdings[0].quantity").value(2.0))
                .andExpect(jsonPath("\$.holdings[0].currentPrice").value(150.0))
                .andExpect(jsonPath("\$.holdings[0].currentValue").value(300.0))
                .andExpect(jsonPath("\$.holdings[0].unrealisedPnL").value(10.0))
                .andExpect(jsonPath("\$.holdings[0].portfolioPercent").value(37.5))
                .andExpect(jsonPath("\$.cash.balance").value(500.0))
                .andExpect(jsonPath("\$.cash.currency").value("USD"))
                .andExpect(jsonPath("\$.cash.portfolioPercent").value(62.5))
        }

        test("getHoldings_happyPath_responseIncludesNonNullInsightsWithAllThreeSubFields") {
            whenever(portfolioQueryService.getHoldings(any(), any())).thenReturn(buildHoldingsResult())

            mockMvc.perform(
                get("/api/v1/portfolio/holdings")
                    .param("accountId", accountId.toString())
                    .param("userId", userId.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("\$.insights").exists())
                .andExpect(jsonPath("\$.insights.assetClassBreakdown").exists())
                .andExpect(jsonPath("\$.insights.stockBreakdown").isArray)
                .andExpect(jsonPath("\$.insights.unrealisedPnLContribution").isArray)
                .andExpect(jsonPath("\$.insights.assetClassBreakdown.totalPortfolioValue").value(800.0))
                .andExpect(jsonPath("\$.insights.assetClassBreakdown.stockPercent").value(37.5))
                .andExpect(jsonPath("\$.insights.assetClassBreakdown.cashPercent").value(62.5))
                .andExpect(jsonPath("\$.insights.stockBreakdown[0].ticker").value("AAPL"))
                .andExpect(jsonPath("\$.insights.stockBreakdown[0].currentValue").value(300.0))
                .andExpect(jsonPath("\$.insights.stockBreakdown[0].percentOfStockPortfolio").value(100.0))
                .andExpect(jsonPath("\$.insights.unrealisedPnLContribution[0].ticker").value("AAPL"))
                .andExpect(jsonPath("\$.insights.unrealisedPnLContribution[0].unrealisedPnL").value(10.0))
        }

        test("getHoldings_noStockPositions_insightsStockBreakdownIsEmptyArray") {
            whenever(portfolioQueryService.getHoldings(any(), any())).thenReturn(buildEmptyStockHoldingsResult())

            mockMvc.perform(
                get("/api/v1/portfolio/holdings")
                    .param("accountId", accountId.toString())
                    .param("userId", userId.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("\$.insights.stockBreakdown").isArray)
                .andExpect(jsonPath("\$.insights.stockBreakdown").isEmpty)
                .andExpect(jsonPath("\$.insights.unrealisedPnLContribution").isArray)
                .andExpect(jsonPath("\$.insights.unrealisedPnLContribution").isEmpty)
        }

        test("getHoldings_zeroTotalPortfolioValue_insightsAssetClassBreakdownPercentsAreNull") {
            whenever(portfolioQueryService.getHoldings(any(), any())).thenReturn(buildZeroTotalValueResult())

            mockMvc.perform(
                get("/api/v1/portfolio/holdings")
                    .param("accountId", accountId.toString())
                    .param("userId", userId.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("\$.insights.assetClassBreakdown.stockPercent").doesNotExist())
                .andExpect(jsonPath("\$.insights.assetClassBreakdown.cashPercent").doesNotExist())
                .andExpect(jsonPath("\$.insights.assetClassBreakdown.totalPortfolioValue").value(0))
        }

        test("getHoldings_accountNotFound_returns404") {
            whenever(portfolioQueryService.getHoldings(any(), any()))
                .thenThrow(PortfolioAccountNotFoundException("Account not found"))

            mockMvc.perform(
                get("/api/v1/portfolio/holdings")
                    .param("accountId", accountId.toString())
                    .param("userId", userId.toString())
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("\$.status").value(HttpStatus.NOT_FOUND.value()))
        }

        test("getHoldings_accountNotOwned_returns403") {
            whenever(portfolioQueryService.getHoldings(any(), any()))
                .thenThrow(PortfolioAccountAccessDeniedException("Access denied"))

            mockMvc.perform(
                get("/api/v1/portfolio/holdings")
                    .param("accountId", accountId.toString())
                    .param("userId", userId.toString())
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("\$.status").value(HttpStatus.FORBIDDEN.value()))
        }

        test("getHoldings_priceDataUnavailable_returns502") {
            whenever(portfolioQueryService.getHoldings(any(), any()))
                .thenThrow(PortfolioPriceUnavailableException("Price data unavailable"))

            mockMvc.perform(
                get("/api/v1/portfolio/holdings")
                    .param("accountId", accountId.toString())
                    .param("userId", userId.toString())
            )
                .andExpect(status().isBadGateway)
                .andExpect(jsonPath("\$.status").value(HttpStatus.BAD_GATEWAY.value()))
        }

        test("getHoldings_balanceDataUnavailable_returns502") {
            whenever(portfolioQueryService.getHoldings(any(), any()))
                .thenThrow(PortfolioBalanceUnavailableException("Balance data unavailable"))

            mockMvc.perform(
                get("/api/v1/portfolio/holdings")
                    .param("accountId", accountId.toString())
                    .param("userId", userId.toString())
            )
                .andExpect(status().isBadGateway)
                .andExpect(jsonPath("\$.status").value(HttpStatus.BAD_GATEWAY.value()))
        }
    }
}
