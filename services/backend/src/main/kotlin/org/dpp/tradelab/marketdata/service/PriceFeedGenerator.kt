package org.dpp.tradelab.marketdata.service

import org.dpp.tradelab.marketdata.model.MarketDataSnapshot

/**
 * Abstraction over the price feed tick source.
 *
 * Each call to [generateTick] returns a list of 1–10 [MarketDataSnapshot] instances,
 * one per randomly selected ticker. Callers (e.g. MarketDataFeedService) must not depend
 * on a specific implementation — wire the implementing @Component via constructor injection.
 */
interface PriceFeedGenerator {

    /**
     * Generates a batch of market data ticks for a random subset of supported tickers.
     *
     * @return between 1 and 10 snapshots (inclusive), one per ticker, with no duplicate tickers
     */
    fun generateTick(): List<MarketDataSnapshot>
}
