package org.dpp.tradelab.marketdata.messaging

import org.dpp.tradelab.marketdata.service.MarketDataFeedService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Receives domain events that affect the market-data feed and delegates
 * all business logic to [MarketDataFeedService].
 *
 * This class contains **no business logic** — each method is a single
 * delegation call. When this domain is extracted to a standalone service,
 * only this class changes (e.g. `@EventListener` → `@KafkaListener`);
 * the service `handle*` methods remain untouched.
 */
@Component
class MarketDataEventListener(
    private val marketDataFeedService: MarketDataFeedService
) {

    @EventListener
    fun onAssetSubscribed(event: AssetSubscribedEvent) =
        marketDataFeedService.handleAssetSubscribed(event)

    @EventListener
    fun onAssetUnsubscribed(event: AssetUnsubscribedEvent) =
        marketDataFeedService.handleAssetUnsubscribed(event)
}
