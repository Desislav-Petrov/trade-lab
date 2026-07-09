package org.dpp.tradelab.portfolio.controller

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.dpp.tradelab.portfolio.exception.PortfolioAccountAccessDeniedException
import org.dpp.tradelab.portfolio.exception.PortfolioAccountNotFoundException
import org.dpp.tradelab.portfolio.exception.PortfolioBalanceUnavailableException
import org.dpp.tradelab.portfolio.exception.PortfolioPriceUnavailableException
import org.dpp.tradelab.portfolio.service.CashHoldingResult
import org.dpp.tradelab.portfolio.service.PortfolioHoldingsResult
import org.dpp.tradelab.portfolio.service.PortfolioQueryService
import org.dpp.tradelab.portfolio.service.StockHoldingResult
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
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
                .andExpect(jsonPath("$.holdings").isArray)
                .andExpect(jsonPath("$.holdings[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.holdings[0].quantity").value(2.0))
                .andExpect(jsonPath("$.holdings[0].currentPrice").value(150.0))
                .andExpect(jsonPath("$.holdings[0].currentValue").value(300.0))
                .andExpect(jsonPath("$.holdings[0].unrealisedPnL").value(10.0))
                .andExpect(jsonPath("$.holdings[0].portfolioPercent").value(37.5))
                .andExpect(jsonPath("$.cash.balance").value(500.0))
                .andExpect(jsonPath("$.cash.currency").value("USD"))
                .andExpect(jsonPath("$.cash.portfolioPercent").value(62.5))
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
                .andExpect(jsonPath("$.status").value(404))
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
                .andExpect(jsonPath("$.status").value(403))
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
                .andExpect(jsonPath("$.status").value(502))
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
                .andExpect(jsonPath("$.status").value(502))
        }
    }
}
