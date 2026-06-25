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
 * On each call to [generateTick], selects between 1 and 10 unique tickers at random from
 * the supported tickers configuration and generates random positive [BigDecimal] values
 * (scale 3) for all four price fields. Intended for use in paper-trading simulation only.
 */
@Component
class RandomPriceFeedGenerator(
    private val supportedTickerConfig: SupportedTickerConfig
) : PriceFeedGenerator {

    override fun generateTick(): List<MarketDataSnapshot> {
        val allTickers = supportedTickerConfig.getAll()
        val tickerEntries = allTickers.entries.toList()

        if (tickerEntries.isEmpty()) return emptyList()

        val count = Random.nextInt(1, minOf(11, tickerEntries.size + 1))
        val selected = tickerEntries.shuffled().take(count)

        val now = Instant.now()

        return selected.map { (ticker, companyName) ->
            MarketDataSnapshot(
                ticker = ticker,
                companyName = companyName,
                currentPrice = randomPositivePrice(),
                open = randomPositivePrice(),
                dayLow = randomPositivePrice(),
                fiftyTwoWeekHigh = randomPositivePrice(),
                updatedAt = now
            )
        }
    }

    private fun randomPositivePrice(): BigDecimal {
        val raw = Random.nextDouble(0.001, 9999.999)
        return BigDecimal(raw).setScale(3, RoundingMode.HALF_UP)
    }
}
