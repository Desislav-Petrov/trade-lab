package org.dpp.tradelab.marketdata.repository

import org.dpp.tradelab.marketdata.model.AssetSubscription
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AssetSubscriptionRepository : JpaRepository<AssetSubscription, UUID> {

    fun findAllByUserIdOrderByTickerAsc(userId: UUID): List<AssetSubscription>

    fun findAllByUserIdAndTickerIn(userId: UUID, tickers: List<String>): List<AssetSubscription>

    fun countByUserId(userId: UUID): Long
}
