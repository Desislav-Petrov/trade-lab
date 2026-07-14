package org.dpp.tradelab.portfolio.service

import org.dpp.tradelab.ledger.api.LedgerAccountApi
import org.dpp.tradelab.ledger.api.LedgerApi
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.marketdata.api.MarketDataApi
import org.dpp.tradelab.portfolio.api.PortfolioApi
import org.dpp.tradelab.portfolio.exception.PortfolioAccountAccessDeniedException
import org.dpp.tradelab.portfolio.exception.PortfolioAccountNotFoundException
import org.dpp.tradelab.portfolio.exception.PortfolioBalanceUnavailableException
import org.dpp.tradelab.portfolio.exception.PortfolioPriceUnavailableException
import org.dpp.tradelab.portfolio.repository.PositionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class StockHoldingResult(
    val ticker: String,
    val quantity: BigDecimal,
    val currentPrice: BigDecimal,
    val currentValue: BigDecimal,
    val minPrice: BigDecimal,
    val maxPrice: BigDecimal,
    val avgPrice: BigDecimal,
    val portfolioPercent: BigDecimal?,
    val unrealisedPnL: BigDecimal
)

data class CashHoldingResult(
    val balance: BigDecimal,
    val currency: String,
    val portfolioPercent: BigDecimal?
)

data class PortfolioHoldingsResult(
    val holdings: List<StockHoldingResult>,
    val cash: CashHoldingResult
)

@Service
class PortfolioQueryService(
    private val positionRepository: PositionRepository,
    private val ledgerApi: LedgerApi,
    private val ledgerAccountApi: LedgerAccountApi,
    private val marketDataApi: MarketDataApi
) : PortfolioApi {

    @Transactional(readOnly = true)
    override fun getPositionQuantity(accountId: UUID, ticker: String): BigDecimal =
        positionRepository.findByAccountIdAndTicker(accountId, ticker)
            .map { it.quantity }
            .orElse(BigDecimal.ZERO)

    @Transactional(readOnly = true)
    fun getHoldings(accountId: UUID, userId: UUID): PortfolioHoldingsResult {
        // Step 1: Validate account ownership via LedgerAccountApi
        val accountSummary = try {
            ledgerAccountApi.getAccount(accountId)
        } catch (ex: AccountNotFoundException) {
            throw PortfolioAccountNotFoundException("Account not found: $accountId")
        }

        if (accountSummary.userId != userId) {
            throw PortfolioAccountAccessDeniedException(
                "Account $accountId does not belong to user $userId"
            )
        }

        // Step 2: Load stock positions with quantity > 0
        val positions = positionRepository.findAllByAccountIdAndQuantityGreaterThan(
            accountId, BigDecimal.ZERO
        )

        // Step 3: Fetch live prices if positions are non-empty
        val priceMap: Map<String, BigDecimal> = if (positions.isNotEmpty()) {
            try {
                marketDataApi.getPrices(positions.map { it.ticker })
            } catch (ex: Exception) {
                throw PortfolioPriceUnavailableException(
                    "Could not load portfolio. Price data unavailable: ${ex.message}"
                )
            }
        } else {
            emptyMap()
        }

        // Step 4: Fetch cash balance
        val balanceResult = try {
            ledgerApi.getBalance(accountId)
        } catch (ex: Exception) {
            throw PortfolioBalanceUnavailableException(
                "Could not load portfolio. Balance data unavailable: ${ex.message}"
            )
        }

        val cashBalance = balanceResult.balance

        // Step 5: Compute derived fields
        val stockHoldings = positions.map { position ->
            val currentPrice = priceMap[position.ticker] ?: BigDecimal.ZERO
            val currentValue = position.quantity.multiply(currentPrice)
            val avgPrice = position.avgPrice ?: BigDecimal.ZERO
            val unrealisedPnL = currentPrice.subtract(avgPrice).multiply(position.quantity)
            StockHoldingResult(
                ticker = position.ticker,
                quantity = position.quantity,
                currentPrice = currentPrice,
                currentValue = currentValue,
                minPrice = position.minPrice,
                maxPrice = position.maxPrice,
                avgPrice = avgPrice,
                portfolioPercent = null, // computed below after totalValue known
                unrealisedPnL = unrealisedPnL
            )
        }

        val totalStockValue = stockHoldings.fold(BigDecimal.ZERO) { acc, h -> acc.add(h.currentValue) }
        val totalValue = totalStockValue.add(cashBalance)

        val finalStockHoldings = stockHoldings.map { holding ->
            val portfolioPercent = if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
                null
            } else {
                holding.currentValue.divide(totalValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal("100"))
                    .setScale(4, RoundingMode.HALF_UP)
            }
            holding.copy(portfolioPercent = portfolioPercent)
        }

        val cashPortfolioPercent = if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            null
        } else {
            cashBalance.divide(totalValue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
                .setScale(4, RoundingMode.HALF_UP)
        }

        return PortfolioHoldingsResult(
            holdings = finalStockHoldings,
            cash = CashHoldingResult(
                balance = cashBalance,
                currency = balanceResult.currency,
                portfolioPercent = cashPortfolioPercent
            )
        )
    }
}
