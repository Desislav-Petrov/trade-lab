package org.dpp.tradelab.marketdata.service

import org.springframework.stereotype.Component

/**
 * Synthetic price feed adapter — wraps [PriceFeedGenerator] and marks the source
 * as the synthetic (simulated) feed for routing purposes.
 *
 * The actual tick generation and dispatch to WebSocket sessions is driven by
 * [MarketDataFeedService.dispatchTicks], which is scheduled at 250 ms. This class
 * serves as the [MarketDataFeedAdapter] marker so that [MarketDataFeedService] can
 * discriminate between synthetic and real (Finnhub) ticks when routing to users.
 */
@Component
class SyntheticPriceFeedAdapter(
    val priceFeedGenerator: PriceFeedGenerator
) : MarketDataFeedAdapter
