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

    // ── getPositionQuantity ──────────────────────────────────────────────────

    test("getPositionQuantity_positionExists_returnsQuantity") {
        val ticker = "AAPL"
        val position = buildPosition(ticker, BigDecimal("5.0000"), BigDecimal("150.0000"))
        whenever(positionRepository.findByAccountIdAndTicker(accountId, ticker))
            .thenReturn(java.util.Optional.of(position))

        val result = service.getPositionQuantity(accountId, ticker)

        result shouldBe BigDecimal("5.0000")
    }

    test("getPositionQuantity_noPosition_returnsZero") {
        val ticker = "AAPL"
        whenever(positionRepository.findByAccountIdAndTicker(accountId, ticker))
            .thenReturn(java.util.Optional.empty())

        val result = service.getPositionQuantity(accountId, ticker)

        result shouldBe BigDecimal.ZERO
    }

    // ── Insights: mixed positive and negative unrealisedPnL ─────────────────

    test("getHoldings_mixedPositiveAndNegativeUnrealisedPnL_insightsContainsBothSigns") {
        val aaplPosition = buildPosition("AAPL", BigDecimal("2.0000"), BigDecimal("100.0000"))
        val googPosition = buildPosition("GOOG", BigDecimal("1.0000"), BigDecimal("200.0000"))

        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(buildAccountSummary())
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO))
            .thenReturn(listOf(aaplPosition, googPosition))
        // AAPL currentPrice > avgPrice → positive PnL; GOOG currentPrice < avgPrice → negative PnL
        whenever(marketDataApi.getPrices(any())).thenReturn(
            mapOf("AAPL" to BigDecimal("120.0000"), "GOOG" to BigDecimal("180.0000"))
        )
        whenever(ledgerApi.getBalance(accountId)).thenReturn(AccountBalanceResult(BigDecimal("500.0000"), "USD"))

        val result = service.getHoldings(accountId, userId)

        val pnlMap = result.insights.unrealisedPnLContribution.associateBy { it.ticker }
        // AAPL: (120 - 100) * 2 = 40
        pnlMap["AAPL"]!!.unrealisedPnL.compareTo(BigDecimal("40.0000")) shouldBe 0
        // GOOG: (180 - 200) * 1 = -20
        pnlMap["GOOG"]!!.unrealisedPnL.compareTo(BigDecimal("-20.0000")) shouldBe 0
    }

    // ── Insights: zero stock positions ───────────────────────────────────────

    test("getHoldings_noStockPositions_insightsStockBreakdownAndPnlAreEmpty") {
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(buildAccountSummary())
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO))
            .thenReturn(emptyList())
        whenever(ledgerApi.getBalance(accountId)).thenReturn(AccountBalanceResult(BigDecimal("1000.0000"), "USD"))

        val result = service.getHoldings(accountId, userId)

        result.insights.stockBreakdown.shouldBeEmpty()
        result.insights.unrealisedPnLContribution.shouldBeEmpty()
    }

    // ── Insights: zero total portfolio value → null percents ─────────────────

    test("getHoldings_zeroTotalPortfolioValue_insightsAssetClassPercentsAreNull") {
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(buildAccountSummary())
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO))
            .thenReturn(emptyList())
        whenever(ledgerApi.getBalance(accountId)).thenReturn(AccountBalanceResult(BigDecimal.ZERO, "USD"))

        val result = service.getHoldings(accountId, userId)

        result.insights.assetClassBreakdown.stockPercent shouldBe null
        result.insights.assetClassBreakdown.cashPercent shouldBe null
        result.insights.assetClassBreakdown.totalPortfolioValue.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    // ── Insights: single stock → 100% stockBreakdown ─────────────────────────

    test("getHoldings_singleStock_insightsStockBreakdownIs100Percent") {
        val position = buildPosition("AAPL", BigDecimal("5.0000"), BigDecimal("100.0000"))
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(buildAccountSummary())
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO))
            .thenReturn(listOf(position))
        whenever(marketDataApi.getPrices(listOf("AAPL"))).thenReturn(mapOf("AAPL" to BigDecimal("110.0000")))
        whenever(ledgerApi.getBalance(accountId)).thenReturn(AccountBalanceResult(BigDecimal.ZERO, "USD"))

        val result = service.getHoldings(accountId, userId)

        result.insights.stockBreakdown shouldHaveSize 1
        result.insights.stockBreakdown[0].percentOfStockPortfolio!!.compareTo(BigDecimal("100.0000")) shouldBe 0
    }

    // ── Insights: multiple stocks → percents sum to 100 ──────────────────────

    test("getHoldings_multipleStocks_insightsStockBreakdownPercentsAreCorrect") {
        val aaplPosition = buildPosition("AAPL", BigDecimal("2.0000"), BigDecimal("100.0000"))
        val msftPosition = buildPosition("MSFT", BigDecimal("3.0000"), BigDecimal("200.0000"))

        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(buildAccountSummary())
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO))
            .thenReturn(listOf(aaplPosition, msftPosition))
        // AAPL currentValue = 2 * 100 = 200; MSFT currentValue = 3 * 200 = 600; totalStock = 800
        whenever(marketDataApi.getPrices(any())).thenReturn(
            mapOf("AAPL" to BigDecimal("100.0000"), "MSFT" to BigDecimal("200.0000"))
        )
        whenever(ledgerApi.getBalance(accountId)).thenReturn(AccountBalanceResult(BigDecimal.ZERO, "USD"))

        val result = service.getHoldings(accountId, userId)

        val breakdownMap = result.insights.stockBreakdown.associateBy { it.ticker }
        // AAPL: 200 / 800 * 100 = 25%
        breakdownMap["AAPL"]!!.percentOfStockPortfolio!!.compareTo(BigDecimal("25.0000")) shouldBe 0
        // MSFT: 600 / 800 * 100 = 75%
        breakdownMap["MSFT"]!!.percentOfStockPortfolio!!.compareTo(BigDecimal("75.0000")) shouldBe 0

        val total = result.insights.stockBreakdown
            .mapNotNull { it.percentOfStockPortfolio }
            .fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        total.compareTo(BigDecimal("100.0000")) shouldBe 0
    }
})
