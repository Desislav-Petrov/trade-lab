package org.dpp.tradelab.marketdata.model

import java.math.BigDecimal
import java.time.Instant

/**
 * In-memory (non-persisted) snapshot of market data for a single ticker at a point in time.
 *
 * This is NOT a JPA entity — it carries no @Entity or @Table annotations and is never stored
 * in the database. Instances are held in the MarketDataFeedService snapshotCache.
 */
data class MarketDataSnapshot(
    val ticker: String,
    val companyName: String,
    val currentPrice: BigDecimal,
    val open: BigDecimal,
    val dayLow: BigDecimal,
    val fiftyTwoWeekHigh: BigDecimal,
    val updatedAt: Instant
)
