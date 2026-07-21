package org.dpp.tradelab.marketdata.service

import org.dpp.tradelab.marketdata.config.SupportedTickerConfig
import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.random.Random

/**
 * Random-price implementation of [PriceFeedGenerator].
 *
 * Each ticker is seeded with an initial price drawn uniformly at random from $200.000–$400.000
 * (scale 3). On every subsequent tick the price moves by a random magnitude in [0.5%, 1.5%]
 * in either direction (50/50). Running `dayLow`, `dayHigh`, and `fiftyTwoWeekHigh` are tracked
 * across ticks; `open` is frozen at the seed price and never changes.
 *
 * Intended for use in paper-trading simulation only.
 */
@Component
class RandomPriceFeedGenerator(
    private val supportedTickerConfig: SupportedTickerConfig
) : PriceFeedGenerator {

    private data class TickerState(
        val companyName: String,
        val currentPrice: BigDecimal,
        val open: BigDecimal,
        val dayLow: BigDecimal,
        val dayHigh: BigDecimal,
        val fiftyTwoWeekHigh: BigDecimal,
    )

    private val tickerState: MutableMap<String, TickerState> = mutableMapOf()

    override fun generateTick(): List<MarketDataSnapshot> {
        val allTickers = supportedTickerConfig.getAll()
        val tickerEntries = allTickers.entries.toList()

        if (tickerEntries.isEmpty()) return emptyList()

        val count = Random.nextInt(1, minOf(11, tickerEntries.size + 1))
        val selected = tickerEntries.shuffled().take(count)

        val now = Instant.now()

        return selected.map { (ticker, companyName) ->
            val state = tickerState[ticker]
            val newState = if (state == null) {
                val seed = seedPrice()
                TickerState(
                    companyName = companyName,
                    currentPrice = seed,
                    open = seed,
                    dayLow = seed,
                    dayHigh = seed,
                    fiftyTwoWeekHigh = seed,
                )
            } else {
                val magnitude = BigDecimal(Random.nextDouble(0.005, 0.015))
                val factor = if (Random.nextBoolean()) {
                    BigDecimal.ONE.add(magnitude)
                } else {
                    BigDecimal.ONE.subtract(magnitude)
                }
                val newPrice = state.currentPrice.multiply(factor).setScale(3, RoundingMode.HALF_UP)
                TickerState(
                    companyName = companyName,
                    currentPrice = newPrice,
                    open = state.open,
                    dayLow = newPrice.min(state.dayLow),
                    dayHigh = newPrice.max(state.dayHigh),
                    fiftyTwoWeekHigh = newPrice.max(state.fiftyTwoWeekHigh),
                )
            }
            tickerState[ticker] = newState
            MarketDataSnapshot(
                ticker = ticker,
                companyName = newState.companyName,
                currentPrice = newState.currentPrice,
                open = newState.open,
                dayLow = newState.dayLow,
                dayHigh = newState.dayHigh,
                fiftyTwoWeekHigh = newState.fiftyTwoWeekHigh,
                updatedAt = now,
            )
        }
    }

    private fun seedPrice(): BigDecimal =
        BigDecimal(Random.nextDouble(200.0, 400.0)).setScale(3, RoundingMode.HALF_UP)
}
