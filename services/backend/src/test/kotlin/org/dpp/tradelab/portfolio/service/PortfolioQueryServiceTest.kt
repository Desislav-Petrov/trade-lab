package org.dpp.tradelab.portfolio.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.ledger.api.AccountBalanceResult
import org.dpp.tradelab.ledger.api.AccountSummary
import org.dpp.tradelab.ledger.api.LedgerAccountApi
import org.dpp.tradelab.ledger.api.LedgerApi
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.marketdata.api.MarketDataApi
import org.dpp.tradelab.portfolio.exception.PortfolioAccountAccessDeniedException
import org.dpp.tradelab.portfolio.exception.PortfolioAccountNotFoundException
import org.dpp.tradelab.portfolio.exception.PortfolioBalanceUnavailableException
import org.dpp.tradelab.portfolio.exception.PortfolioPriceUnavailableException
import org.dpp.tradelab.portfolio.model.AssetType
import org.dpp.tradelab.portfolio.model.Position
import org.dpp.tradelab.portfolio.repository.PositionRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class PortfolioQueryServiceTest : FunSpec({

    val positionRepository = mock<PositionRepository>()
    val ledgerApi = mock<LedgerApi>()
    val ledgerAccountApi = mock<LedgerAccountApi>()
    val marketDataApi = mock<MarketDataApi>()

    val service = PortfolioQueryService(positionRepository, ledgerApi, ledgerAccountApi, marketDataApi)

    val userId = UUID.randomUUID()
    val accountId = UUID.randomUUID()

    fun buildAccountSummary(ownerId: UUID = userId) = AccountSummary(
        id = accountId,
        userId = ownerId,
        currency = "USD",
        balance = BigDecimal("1000.0000"),
        status = "active"
    )

    fun buildPosition(ticker: String, quantity: BigDecimal, avgPrice: BigDecimal) = Position(
        positionId = UUID.randomUUID(),
        userId = userId,
        accountId = accountId,
        ticker = ticker,
        assetType = AssetType.STOCK,
        quantity = quantity,
        totalCost = quantity.multiply(avgPrice),
        avgPrice = avgPrice,
        minPrice = avgPrice.subtract(BigDecimal("10.0000")),
        maxPrice = avgPrice.add(BigDecimal("10.0000")),
        lastUpdated = Instant.now()
    )

    beforeEach {
        org.mockito.kotlin.reset(positionRepository, ledgerApi, ledgerAccountApi, marketDataApi)
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    test("getHoldings_happyPath_returnsCorrectEnrichedResponse") {
        val aaplQty = BigDecimal("2.0000")
        val aaplAvg = BigDecimal("140.0000")
        val aaplCurrent = BigDecimal("150.0000")
        val cashBalance = BigDecimal("500.0000")

        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(buildAccountSummary())
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO))
            .thenReturn(listOf(buildPosition("AAPL", aaplQty, aaplAvg)))
        whenever(marketDataApi.getPrices(listOf("AAPL"))).thenReturn(mapOf("AAPL" to aaplCurrent))
        whenever(ledgerApi.getBalance(accountId)).thenReturn(AccountBalanceResult(cashBalance, "USD"))

        val result = service.getHoldings(accountId, userId)

        result.holdings shouldHaveSize 1
        val holding = result.holdings[0]
        holding.ticker shouldBe "AAPL"
        holding.quantity shouldBe aaplQty
        holding.currentPrice shouldBe aaplCurrent
        holding.currentValue shouldBe aaplQty.multiply(aaplCurrent) // 300.0000
        holding.avgPrice shouldBe aaplAvg
        // unrealisedPnL = (150 - 140) * 2 = 20
        holding.unrealisedPnL shouldBe BigDecimal("10.0000").multiply(aaplQty)
        holding.portfolioPercent shouldNotBe null

        result.cash.balance shouldBe cashBalance
        result.cash.currency shouldBe "USD"
        result.cash.portfolioPercent shouldNotBe null
    }

    // ── Account not owned by user ────────────────────────────────────────────

    test("getHoldings_accountNotOwnedByUser_throwsPortfolioAccountAccessDeniedException") {
        val differentUserId = UUID.randomUUID()
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(buildAccountSummary(ownerId = differentUserId))

        shouldThrow<PortfolioAccountAccessDeniedException> {
            service.getHoldings(accountId, userId)
        }
    }

    // ── Account not found ────────────────────────────────────────────────────

    test("getHoldings_accountNotFound_throwsPortfolioAccountNotFoundException") {
        whenever(ledgerAccountApi.getAccount(accountId)).thenThrow(AccountNotFoundException(accountId))

        shouldThrow<PortfolioAccountNotFoundException> {
            service.getHoldings(accountId, userId)
        }
    }

    // ── Market Data call fails ───────────────────────────────────────────────

    test("getHoldings_marketDataCallFails_throwsPortfolioPriceUnavailableException") {
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(buildAccountSummary())
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO))
            .thenReturn(listOf(buildPosition("AAPL", BigDecimal("1.0000"), BigDecimal("100.0000"))))
        whenever(marketDataApi.getPrices(any())).thenThrow(RuntimeException("Price feed down"))

        shouldThrow<PortfolioPriceUnavailableException> {
            service.getHoldings(accountId, userId)
        }
    }

    // ── Ledger balance call fails ────────────────────────────────────────────

    test("getHoldings_ledgerBalanceFails_throwsPortfolioBalanceUnavailableException") {
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(buildAccountSummary())
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO))
            .thenReturn(emptyList())
        whenever(ledgerApi.getBalance(accountId)).thenThrow(RuntimeException("Ledger unavailable"))

        shouldThrow<PortfolioBalanceUnavailableException> {
            service.getHoldings(accountId, userId)
        }
    }

    // ── No stock positions ────────────────────────────────────────────────────

    test("getHoldings_noStockPositions_returnsEmptyHoldingsListWithCash") {
        val cashBalance = BigDecimal("1000.0000")
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(buildAccountSummary())
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO))
            .thenReturn(emptyList())
        whenever(ledgerApi.getBalance(accountId)).thenReturn(AccountBalanceResult(cashBalance, "USD"))

        val result = service.getHoldings(accountId, userId)

        result.holdings.shouldBeEmpty()
        result.cash.balance shouldBe cashBalance
        result.cash.currency shouldBe "USD"
        // Cash is 100% of portfolio
        result.cash.portfolioPercent shouldBe BigDecimal("100.0000")
    }

    // ── totalValue = 0 ───────────────────────────────────────────────────────

    test("getHoldings_totalValueIsZero_allPortfolioPercentFieldsAreNull") {
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(buildAccountSummary())
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO))
            .thenReturn(emptyList())
        whenever(ledgerApi.getBalance(accountId)).thenReturn(AccountBalanceResult(BigDecimal.ZERO, "USD"))

        val result = service.getHoldings(accountId, userId)

        result.holdings.shouldBeEmpty()
        result.cash.portfolioPercent shouldBe null
    }

    test("getHoldings_stockPositionsWithZeroTotalValue_portfolioPercentsAreNull") {
        val position = buildPosition("AAPL", BigDecimal("2.0000"), BigDecimal("100.0000"))
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(buildAccountSummary())
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO))
            .thenReturn(listOf(position))
        // currentPrice = 0, balance = 0 → totalValue = 0
        whenever(marketDataApi.getPrices(listOf("AAPL"))).thenReturn(mapOf("AAPL" to BigDecimal.ZERO))
        whenever(ledgerApi.getBalance(accountId)).thenReturn(AccountBalanceResult(BigDecimal.ZERO, "USD"))

        val result = service.getHoldings(accountId, userId)

        result.holdings[0].portfolioPercent shouldBe null
        result.cash.portfolioPercent shouldBe null
    }

    test("getPositionQuantity_positionExists_returnsQuantity") {
        whenever(positionRepository.findByAccountIdAndTicker(accountId, "AAPL"))
            .thenReturn(Optional.of(buildPosition("AAPL", BigDecimal("2.0000"), BigDecimal("100.0000"))))

        service.getPositionQuantity(accountId, "AAPL") shouldBe BigDecimal("2.0000")
    }

    test("getPositionQuantity_noPosition_returnsZero") {
        whenever(positionRepository.findByAccountIdAndTicker(accountId, "AAPL"))
            .thenReturn(Optional.empty())

        service.getPositionQuantity(accountId, "AAPL") shouldBe BigDecimal.ZERO
    }
})
