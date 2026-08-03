package org.dpp.tradelab.marketdata.service

import org.dpp.tradelab.marketdata.messaging.MarketDataTickEvent
import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.dpp.tradelab.user.model.FeedType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Synthetic price feed adapter — drives simulated price ticks at 250 ms intervals.
 *
 * On each scheduled invocation the adapter calls [PriceFeedGenerator.generateTick] and
 * publishes each resulting [MarketDataSnapshot] to the Spring application event bus via
 * [emitSnapshotEvent]. [org.dpp.tradelab.marketdata.messaging.MarketDataEventListener]
 * picks up the emitted [MarketDataTickEvent] and routes ticks to users whose feed type
 * is [FeedType.SYNTHETIC].
 */
@Component
class SyntheticPriceFeedAdapter(
    private val priceFeedGenerator: PriceFeedGenerator,
    private val eventPublisher: ApplicationEventPublisher
) : MarketDataFeedAdapter {

    @Scheduled(fixedDelay = 250)
    fun dispatchTicks() {
        val ticks = priceFeedGenerator.generateTick()
        ticks.forEach { snapshot -> emitSnapshotEvent(snapshot) }
    }

    override fun emitSnapshotEvent(snapshot: MarketDataSnapshot) {
        eventPublisher.publishEvent(MarketDataTickEvent(snapshot = snapshot, feedType = FeedType.SYNTHETIC))
    }
}
