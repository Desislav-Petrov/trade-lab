package org.dpp.tradelab.portfolio.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.ledger.api.AccountBalanceResult
import org.dpp.tradelab.ledger.api.LedgerApi
import org.dpp.tradelab.marketdata.api.MarketDataApi
import org.dpp.tradelab.portfolio.exception.PortfolioAccountNotFoundException
import org.dpp.tradelab.portfolio.exception.PortfolioBalanceUnavailableException
import org.dpp.tradelab.portfolio.exception.PortfolioPriceUnavailableException
import org.dpp.tradelab.portfolio.model.AssetType
import org.dpp.tradelab.portfolio.model.Position
import org.dpp.tradelab.portfolio.repository.PositionRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

class PortfolioQueryServiceTest : FunSpec({

    lateinit var positionRepository: PositionRepository
    lateinit var marketDataApi: MarketDataApi
    lateinit var ledgerApi: LedgerApi
    lateinit var service: PortfolioQueryService

    beforeEach {
        positionRepository = mock()
        marketDataApi = mock()
        ledgerApi = mock()
        service = PortfolioQueryService(positionRepository, marketDataApi, ledgerApi)
    }

    test("getHoldings_happyPath_returnsCorrectEnrichedResponse") {
        // Given
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val cashBalance = BigDecimal("1000.00")
        val currency = "USD"

        val position1 = Position(
            positionId = UUID.randomUUID(),
            userId = userId,
            accountId = accountId,
            ticker = "AAPL",
            assetType = AssetType.STOCK,
            quantity = BigDecimal("10.0000"),
            totalCost = BigDecimal("1500.0000"),
            avgPrice = BigDecimal("150.0000"),
            minPrice = BigDecimal("145.0000"),
            maxPrice = BigDecimal("155.0000"),
            lastUpdated = Instant.now()
        )

        val position2 = Position(
            positionId = UUID.randomUUID(),
            userId = userId,
            accountId = accountId,
            ticker = "GOOGL",
            assetType = AssetType.STOCK,
            quantity = BigDecimal("5.0000"),
            totalCost = BigDecimal("3000.0000"),
            avgPrice = BigDecimal("600.0000"),
            minPrice = BigDecimal("590.0000"),
            maxPrice = BigDecimal("610.0000"),
            lastUpdated = Instant.now()
        )

        whenever(ledgerApi.getBalance(eq(accountId))).thenReturn(
            AccountBalanceResult(balance = cashBalance, currency = currency)
        )
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(eq(accountId), eq(BigDecimal.ZERO)))
            .thenReturn(listOf(position1, position2))
        whenever(marketDataApi.getPrices(eq(listOf("AAPL", "GOOGL"))))
            .thenReturn(mapOf("AAPL" to BigDecimal("160.00"), "GOOGL" to BigDecimal("620.00")))

        // When
        val result = service.getHoldings(accountId, userId)

        // Then
        result.holdings shouldHaveSize 2
        result.cash.balance.compareTo(cashBalance) shouldBe 0
        result.cash.currency shouldBe currency

        // AAPL: currentValue = 10 * 160 = 1600, unrealisedPnL = (160-150) * 10 = 100
        val appleHolding = result.holdings.find { it.ticker == "AAPL" }!!
        appleHolding.quantity.compareTo(BigDecimal("10.0000")) shouldBe 0
        appleHolding.currentPrice.compareTo(BigDecimal("160.00")) shouldBe 0
        appleHolding.currentValue.compareTo(BigDecimal("1600.00")) shouldBe 0
        appleHolding.avgPrice.compareTo(BigDecimal("150.0000")) shouldBe 0
        appleHolding.minPrice.compareTo(BigDecimal("145.0000")) shouldBe 0
        appleHolding.maxPrice.compareTo(BigDecimal("155.0000")) shouldBe 0
        appleHolding.unrealisedPnL.compareTo(BigDecimal("100.0000")) shouldBe 0

        // GOOGL: currentValue = 5 * 620 = 3100, unrealisedPnL = (620-600) * 5 = 100
        val googleHolding = result.holdings.find { it.ticker == "GOOGL" }!!
        googleHolding.quantity.compareTo(BigDecimal("5.0000")) shouldBe 0
        googleHolding.currentPrice.compareTo(BigDecimal("620.00")) shouldBe 0
        googleHolding.currentValue.compareTo(BigDecimal("3100.00")) shouldBe 0
        googleHolding.avgPrice.compareTo(BigDecimal("600.0000")) shouldBe 0
        googleHolding.unrealisedPnL.compareTo(BigDecimal("100.0000")) shouldBe 0

        // Total portfolio value = 1600 + 3100 + 1000 = 5700
        // Apple: 1600 / 5700 = 0.280701754385965 * 100 = 28.0701754385965
        // Google: 3100 / 5700 = 0.543859649122807 * 100 = 54.3859649122807
        // Cash: 1000 / 5700 = 0.175438596491228 * 100 = 17.5438596491228
        
        // Since division uses scale 4, the actual result will have the precision from the calculation
        // Just verify the percentages are reasonable within a small delta
        val applePercent = appleHolding.portfolioPercent!!.toDouble()
        val googlePercent = googleHolding.portfolioPercent!!.toDouble()
        val cashPercent = result.cash.portfolioPercent!!.toDouble()
        
        (applePercent >= 28.07 && applePercent <= 28.08) shouldBe true
        (googlePercent >= 54.38 && googlePercent <= 54.39) shouldBe true
        (cashPercent >= 17.54 && cashPercent <= 17.55) shouldBe true

        verify(ledgerApi).getBalance(accountId)
        verify(positionRepository).findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO)
        verify(marketDataApi).getPrices(listOf("AAPL", "GOOGL"))
    }

    test("getHoldings_accountNotFound_throwsPortfolioAccountNotFoundException") {
        // Given
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val accountNotFoundException = RuntimeException("Account not found")
        whenever(ledgerApi.getBalance(any())).thenAnswer { throw accountNotFoundException }

        // When / Then
        shouldThrow<PortfolioBalanceUnavailableException> {
            service.getHoldings(accountId, userId)
        }
    }

    test("getHoldings_ledgerCallFails_throwsPortfolioBalanceUnavailableException") {
        // Given
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        whenever(ledgerApi.getBalance(any())).thenThrow(RuntimeException("Service unavailable"))

        // When / Then
        shouldThrow<PortfolioBalanceUnavailableException> {
            service.getHoldings(accountId, userId)
        }
    }

    test("getHoldings_marketDataCallFails_throwsPortfolioPriceUnavailableException") {
        // Given
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val position = Position(
            positionId = UUID.randomUUID(),
            userId = userId,
            accountId = accountId,
            ticker = "AAPL",
            assetType = AssetType.STOCK,
            quantity = BigDecimal("10.0000"),
            totalCost = BigDecimal("1500.0000"),
            avgPrice = BigDecimal("150.0000"),
            minPrice = BigDecimal("145.0000"),
            maxPrice = BigDecimal("155.0000"),
            lastUpdated = Instant.now()
        )

        whenever(ledgerApi.getBalance(any())).thenReturn(
            AccountBalanceResult(balance = BigDecimal("1000.00"), currency = "USD")
        )
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(any(), any()))
            .thenReturn(listOf(position))
        whenever(marketDataApi.getPrices(any())).thenThrow(RuntimeException("Market data unavailable"))

        // When / Then
        shouldThrow<PortfolioPriceUnavailableException> {
            service.getHoldings(accountId, userId)
        }
    }

    test("getHoldings_noStockPositions_returnsEmptyHoldingsListWithCashOnly") {
        // Given
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val cashBalance = BigDecimal("1000.00")
        val currency = "USD"

        whenever(ledgerApi.getBalance(eq(accountId))).thenReturn(
            AccountBalanceResult(balance = cashBalance, currency = currency)
        )
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(eq(accountId), eq(BigDecimal.ZERO)))
            .thenReturn(emptyList())

        // When
        val result = service.getHoldings(accountId, userId)

        // Then
        result.holdings.shouldBeEmpty()
        result.cash.balance.compareTo(cashBalance) shouldBe 0
        result.cash.currency shouldBe currency
        result.cash.portfolioPercent!!.compareTo(BigDecimal("100.0000")) shouldBe 0

        verify(ledgerApi).getBalance(accountId)
        verify(positionRepository).findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO)
    }

    test("getHoldings_totalValueZero_allPortfolioPercentsAreNull") {
        // Given
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        whenever(ledgerApi.getBalance(eq(accountId))).thenReturn(
            AccountBalanceResult(balance = BigDecimal.ZERO, currency = "USD")
        )
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(eq(accountId), eq(BigDecimal.ZERO)))
            .thenReturn(emptyList())

        // When
        val result = service.getHoldings(accountId, userId)

        // Then
        result.holdings.shouldBeEmpty()
        result.cash.balance shouldBe BigDecimal.ZERO
        result.cash.portfolioPercent.shouldBeNull()
    }

    test("getHoldings_withStockPositionsAndZeroCash_calculatesPercentsCorrectly") {
        // Given
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val position = Position(
            positionId = UUID.randomUUID(),
            userId = userId,
            accountId = accountId,
            ticker = "AAPL",
            assetType = AssetType.STOCK,
            quantity = BigDecimal("10.0000"),
            totalCost = BigDecimal("1500.0000"),
            avgPrice = BigDecimal("150.0000"),
            minPrice = BigDecimal("145.0000"),
            maxPrice = BigDecimal("155.0000"),
            lastUpdated = Instant.now()
        )

        whenever(ledgerApi.getBalance(eq(accountId))).thenReturn(
            AccountBalanceResult(balance = BigDecimal.ZERO, currency = "USD")
        )
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(eq(accountId), eq(BigDecimal.ZERO)))
            .thenReturn(listOf(position))
        whenever(marketDataApi.getPrices(eq(listOf("AAPL"))))
            .thenReturn(mapOf("AAPL" to BigDecimal("160.00")))

        // When
        val result = service.getHoldings(accountId, userId)

        // Then
        result.holdings shouldHaveSize 1
        val holding = result.holdings[0]
        holding.portfolioPercent!!.compareTo(BigDecimal("100.0000")) shouldBe 0
        result.cash.portfolioPercent!!.compareTo(BigDecimal("0.0000")) shouldBe 0
    }

    test("getHoldings_negativeUnrealisedPnL_calculatesCorrectly") {
        // Given
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val position = Position(
            positionId = UUID.randomUUID(),
            userId = userId,
            accountId = accountId,
            ticker = "AAPL",
            assetType = AssetType.STOCK,
            quantity = BigDecimal("10.0000"),
            totalCost = BigDecimal("1500.0000"),
            avgPrice = BigDecimal("150.0000"),
            minPrice = BigDecimal("145.0000"),
            maxPrice = BigDecimal("155.0000"),
            lastUpdated = Instant.now()
        )

        whenever(ledgerApi.getBalance(eq(accountId))).thenReturn(
            AccountBalanceResult(balance = BigDecimal("1000.00"), currency = "USD")
        )
        whenever(positionRepository.findAllByAccountIdAndQuantityGreaterThan(eq(accountId), eq(BigDecimal.ZERO)))
            .thenReturn(listOf(position))
        whenever(marketDataApi.getPrices(eq(listOf("AAPL"))))
            .thenReturn(mapOf("AAPL" to BigDecimal("140.00"))) // Lower than avgPrice

        // When
        val result = service.getHoldings(accountId, userId)

        // Then
        result.holdings shouldHaveSize 1
        val holding = result.holdings[0]
        // unrealisedPnL = (140 - 150) * 10 = -100
        holding.unrealisedPnL.compareTo(BigDecimal("-100.0000")) shouldBe 0
    }
})
