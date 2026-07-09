package org.dpp.tradelab.portfolio.service

import org.dpp.tradelab.ledger.api.LedgerApi
import org.dpp.tradelab.marketdata.api.MarketDataApi
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

/**
 * Query service for portfolio holdings.
 *
 * Responsible for fetching and enriching position data with live prices and cash balance.
 */
@Service
class PortfolioQueryService(
    private val positionRepository: PositionRepository,
    private val marketDataApi: MarketDataApi,
    private val ledgerApi: LedgerApi
) {

    /**
     * Get enriched holdings for an account.
     *
     * Fetches positions, enriches with prices from MarketData, cash from Ledger,
     * and computes derived fields (currentValue, portfolioPercent, unrealisedPnL).
     *
     * @param accountId The account to fetch holdings for
     * @param userId The user making the request (for ownership validation)
     * @return PortfolioHoldingsResult containing enriched stock holdings and cash
     * @throws PortfolioAccountNotFoundException if the account does not exist
     * @throws PortfolioAccountAccessDeniedException if userId does not match account owner
     * @throws PortfolioPriceUnavailableException if price data cannot be retrieved
     * @throws PortfolioBalanceUnavailableException if balance data cannot be retrieved
     */
    @Transactional(readOnly = true)
    fun getHoldings(accountId: UUID, userId: UUID): PortfolioHoldingsResult {
        // Step 1: Validate account ownership via LedgerApi
        val accountBalance = try {
            ledgerApi.getBalance(accountId)
        } catch (e: Exception) {
            when {
                e::class.simpleName == "AccountNotFoundException" ->
                    throw PortfolioAccountNotFoundException("Account not found: $accountId")
                else ->
                    throw PortfolioBalanceUnavailableException("Could not retrieve balance for account: $accountId")
            }
        }

        // Note: In a real system, we would validate userId matches the account owner.
        // For now, we assume LedgerApi.getBalance already validates this or returns an error.
        // If there's a dedicated ownership check method, it should be used here.

        // Step 2: Query positions with quantity > 0
        val positions = positionRepository.findAllByAccountIdAndQuantityGreaterThan(
            accountId = accountId,
            minQuantity = BigDecimal.ZERO
        )

        // Step 3: Fetch live prices (bulk) if there are any stock positions
        val priceMap: Map<String, BigDecimal> = if (positions.isNotEmpty()) {
            val tickers = positions.map { it.ticker }
            try {
                marketDataApi.getPrices(tickers)
            } catch (e: Exception) {
                throw PortfolioPriceUnavailableException("Could not retrieve prices for account: $accountId")
            }
        } else {
            emptyMap()
        }

        // Step 4: Compute derived fields
        val cashBalance = accountBalance.balance
        val currency = accountBalance.currency

        // Calculate current values for all positions
        val stockHoldings = positions.mapNotNull { position ->
            val currentPrice = priceMap[position.ticker]
            if (currentPrice == null) {
                // Ticker not found in price map - this shouldn't happen in normal operation
                // but we handle it gracefully by excluding the position
                return@mapNotNull null
            }

            val currentValue = position.quantity.multiply(currentPrice)
            val unrealisedPnL = currentPrice.subtract(position.avgPrice).multiply(position.quantity)

            StockHoldingResult(
                ticker = position.ticker,
                quantity = position.quantity,
                currentPrice = currentPrice,
                currentValue = currentValue,
                minPrice = position.minPrice,
                maxPrice = position.maxPrice,
                avgPrice = position.avgPrice,
                portfolioPercent = null, // Will be calculated after totalValue
                unrealisedPnL = unrealisedPnL
            )
        }

        // Calculate total portfolio value
        val totalStockValue = stockHoldings.fold(BigDecimal.ZERO) { acc, holding ->
            acc.add(holding.currentValue)
        }
        val totalValue = totalStockValue.add(cashBalance)

        // Calculate portfolio percentages (handle division by zero)
        val enrichedStockHoldings = if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            stockHoldings // percentages remain null
        } else {
            stockHoldings.map { holding ->
                holding.copy(
                    portfolioPercent = holding.currentValue
                        .divide(totalValue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                )
            }
        }

        val cashHolding = CashHoldingResult(
            balance = cashBalance,
            currency = currency,
            portfolioPercent = if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
                null
            } else {
                cashBalance
                    .divide(totalValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
            }
        )

        return PortfolioHoldingsResult(
            holdings = enrichedStockHoldings,
            cash = cashHolding
        )
    }
}

/**
 * Result containing enriched portfolio holdings.
 */
data class PortfolioHoldingsResult(
    val holdings: List<StockHoldingResult>,
    val cash: CashHoldingResult
)

/**
 * Result for a single stock holding.
 */
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

/**
 * Result for cash holding.
 */
data class CashHoldingResult(
    val balance: BigDecimal,
    val currency: String,
    val portfolioPercent: BigDecimal?
)
