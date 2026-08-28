package org.dpp.tradelab.portfolio.controller

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.dpp.tradelab.portfolio.exception.PortfolioAccountAccessDeniedException
import org.dpp.tradelab.portfolio.exception.PortfolioAccountNotFoundException
import org.dpp.tradelab.portfolio.exception.PortfolioBalanceUnavailableException
import org.dpp.tradelab.portfolio.exception.PortfolioPriceUnavailableException
import org.dpp.tradelab.portfolio.model.AssetType
import org.dpp.tradelab.portfolio.model.FillSide
import org.dpp.tradelab.portfolio.model.PositionFill
import org.dpp.tradelab.portfolio.service.AssetClassBreakdown
import org.dpp.tradelab.portfolio.service.CashHoldingResult
import org.dpp.tradelab.portfolio.service.FillHistoryPage
import org.dpp.tradelab.portfolio.service.PortfolioHoldingsResult
import org.dpp.tradelab.portfolio.service.PortfolioInsights
import org.dpp.tradelab.portfolio.service.PortfolioQueryService
import org.dpp.tradelab.portfolio.service.StockBreakdownEntry
import org.dpp.tradelab.portfolio.service.StockHoldingResult
import org.dpp.tradelab.portfolio.service.UnrealisedPnLEntry
import org.dpp.tradelab.user.service.JwtService
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID


@SpringBootTest
@AutoConfigureMockMvc
class PortfolioApiDelegateImplTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val jwtService: JwtService,
    @MockitoBean val portfolioQueryService: PortfolioQueryService
) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        fun authenticatedFillsRequest() =
            get("/api/v1/portfolio/fills")
                .header(HttpHeaders.AUTHORIZATION, "Bea" + "rer " + jwtService.issueToken(userId))
                .param("accountId", accountId.toString())

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

        fun buildPositionFill(
            ticker: String,
            side: FillSide,
            filledAt: Instant
        ) = PositionFill(
            id = UUID.randomUUID(),
            userId = userId,
            accountId = accountId,
            ticker = ticker,
            assetType = AssetType.STOCK,
            side = side,
            executionPrice = BigDecimal("150.0000"),
            quantity = BigDecimal("2.0000"),
            filledAt = filledAt,
            idempotencyKey = UUID.randomUUID()
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

        test("getFills_happyPath_returns200WithFillsAndPaginationMetadata") {
            val firstFill = buildPositionFill("AAPL", FillSide.BUY, Instant.parse("2026-08-28T10:00:00Z"))
            val secondFill = buildPositionFill("AAPL", FillSide.SELL, Instant.parse("2026-08-28T11:00:00Z"))
            whenever(portfolioQueryService.getFillHistory(any(), any(), any(), any())).thenReturn(
                FillHistoryPage(
                    fills = mapOf("AAPL" to listOf(firstFill, secondFill)),
                    page = 0,
                    size = 100,
                    totalPages = 1,
                    totalElements = 2
                )
            )
            mockMvc.perform(
                authenticatedFillsRequest()
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("\$.page").value(0))
                .andExpect(jsonPath("\$.size").value(100))
                .andExpect(jsonPath("\$.totalPages").value(1))
                .andExpect(jsonPath("\$.totalElements").value(2))
                .andExpect(jsonPath("\$.fills[0].ticker").value("AAPL"))
                .andExpect(jsonPath("\$.fills[0].dataPoints[0].filledAt").value("2026-08-28T10:00:00Z"))
                .andExpect(jsonPath("\$.fills[0].dataPoints[0].executionPrice").value(150.0))
                .andExpect(jsonPath("\$.fills[0].dataPoints[0].quantity").value(2.0))
                .andExpect(jsonPath("\$.fills[0].dataPoints[0].side").value("BUY"))
                .andExpect(jsonPath("\$.fills[0].dataPoints[1].side").value("SELL"))
        }
        test("getFills_noFills_returns200WithEmptyFillsAndZeroTotalElements") {
            whenever(portfolioQueryService.getFillHistory(any(), any(), any(), any())).thenReturn(
                FillHistoryPage(
                    fills = emptyMap(),
                    page = 0,
                    size = 100,
                    totalPages = 0,
                    totalElements = 0
                )
            )
            mockMvc.perform(
                authenticatedFillsRequest()
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("\$.fills").isArray)
                .andExpect(jsonPath("\$.fills").isEmpty)
                .andExpect(jsonPath("\$.totalElements").value(0))
        }

        test("getFills_sizeGreaterThan100_returns400") {
            mockMvc.perform(
                authenticatedFillsRequest()
                    .param("size", "101")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("\$.status").value(HttpStatus.BAD_REQUEST.value()))
        }

        test("getFills_unauthenticated_returns401") {
            mockMvc.perform(
                get("/api/v1/portfolio/fills")
                    .param("accountId", accountId.toString())
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("\$.status").value(HttpStatus.UNAUTHORIZED.value()))
        }
    }
}
