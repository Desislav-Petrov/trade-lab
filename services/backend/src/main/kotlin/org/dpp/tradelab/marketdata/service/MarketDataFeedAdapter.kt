package org.dpp.tradelab.marketdata.service

import org.dpp.tradelab.marketdata.model.MarketDataSnapshot

/**
 * Abstraction over a market data feed source.
 *
 * Implementations are self-contained Spring components that activate themselves
 * (e.g. via [@PostConstruct] and [@Scheduled]) and publish price snapshots to the
 * Spring application event bus by calling [emitSnapshotEvent].
 *
 * [MarketDataFeedService] (via [org.dpp.tradelab.marketdata.messaging.MarketDataEventListener])
 * listens for [org.dpp.tradelab.marketdata.messaging.MarketDataTickEvent] and routes each
 * snapshot to the appropriate subscribers based on their configured feed type.
 */
interface MarketDataFeedAdapter {

    /**
     * Publishes [snapshot] as a [org.dpp.tradelab.marketdata.messaging.MarketDataTickEvent]
     * on the Spring application event bus so that [MarketDataFeedService] can route it to
     * interested subscribers.
     */
    fun emitSnapshotEvent(snapshot: MarketDataSnapshot)
}
