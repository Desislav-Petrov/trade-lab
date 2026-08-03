package org.dpp.tradelab.marketdata.messaging

import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.dpp.tradelab.user.model.FeedType

/**
 * Spring application event published by [MarketDataFeedAdapter] implementations
 * whenever a new price snapshot is available.
 *
 * [feedType] indicates the source of the snapshot: [FeedType.SYNTHETIC] for simulated data
 * and [FeedType.REAL] for live Finnhub data. The [MarketDataFeedService] listens for this event
 * and routes the snapshot only to users whose feed-type matches the source.
 */
data class MarketDataTickEvent(
    val snapshot: MarketDataSnapshot,
    val feedType: FeedType
)
