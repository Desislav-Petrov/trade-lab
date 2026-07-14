package org.dpp.tradelab.portfolio.repository

import org.dpp.tradelab.portfolio.model.Position
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

@Repository
interface PositionRepository : JpaRepository<Position, UUID> {

    fun findByUserIdAndAccountIdAndTicker(
        userId: UUID,
        accountId: UUID,
        ticker: String
    ): Optional<Position>

    fun findByAccountIdAndTicker(
        accountId: UUID,
        ticker: String
    ): Optional<Position>

    fun findAllByAccountIdAndQuantityGreaterThan(
        accountId: UUID,
        minQuantity: BigDecimal
    ): List<Position>
}
