package org.dpp.tradelab.marketdata.messaging

import java.time.Instant
import java.util.UUID

data class AssetUnsubscribedEvent(
    val userId: UUID,
    val tickers: List<String>,
    val timestamp: Instant
)
