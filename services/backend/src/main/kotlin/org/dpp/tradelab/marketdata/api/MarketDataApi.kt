package org.dpp.tradelab.marketdata.api

import java.math.BigDecimal

/**
 * Cross-domain synchronous interface for reading current market prices and
 * checking whether a ticker is supported.
 *
 * Exposed by the Market Data domain to be consumed by other domains (e.g. Stock Trading).
 * Consumers import only this interface — never anything from marketdata.model,
 * marketdata.config, or marketdata.service.
 *
 * [getCurrentPrice] throws [IllegalStateException] if the ticker has no cache entry
 * (missing entry is a programming error — cache is seeded at startup).
 *
 * [getPrices] returns a map of ticker to current price for all requested tickers that
 * are present in the cache. Tickers not found in the cache are omitted from the result
 * (no exception thrown). Returns an empty map when the input list is empty.
 */
interface MarketDataApi {
    fun getCurrentPrice(ticker: String): BigDecimal
    fun isTickerSupported(ticker: String): Boolean
    fun getPrices(tickers: List<String>): Map<String, BigDecimal>
}
