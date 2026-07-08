package org.dpp.tradelab.marketdata.api

/**
 * Cross-domain synchronous interface for checking whether a ticker is supported.
 *
 * Exposed by the Market Data domain to be consumed by other domains (e.g. Stock Trading).
 * Consumers import only this interface — never anything from marketdata.config or marketdata.service.
 */
interface MarketDataSupportedTickersApi {
    fun isTickerSupported(ticker: String): Boolean
}
