package org.dpp.tradelab.marketdata.messaging

import org.dpp.tradelab.marketdata.service.MarketDataFeedService
import org.dpp.tradelab.user.messaging.UserSettingsChangedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Receives [UserSettingsChangedEvent] from the User domain and delegates
 * all business logic to [MarketDataFeedService].
 *
 * This class contains **no business logic** — each method is a single
 * delegation call. When this domain is extracted to a standalone service,
 * only this class changes (e.g. `@EventListener` → `@KafkaListener`);
 * the service `handle*` methods remain untouched.
 */
@Component("marketDataUserEventListener")
class UserEventListener(
    private val marketDataFeedService: MarketDataFeedService
) {

    @EventListener
    fun onUserSettingsChanged(event: UserSettingsChangedEvent) =
        marketDataFeedService.handleUserSettingsChanged(event)
}
