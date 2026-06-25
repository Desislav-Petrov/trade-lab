package org.dpp.tradelab.marketdata.messaging

import java.time.Instant
import java.util.UUID

data class AssetSubscribedEvent(
    val userId: UUID,
    val tickers: List<String>,
    val timestamp: Instant
)
