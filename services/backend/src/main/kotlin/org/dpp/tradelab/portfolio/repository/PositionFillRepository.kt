package org.dpp.tradelab.portfolio.repository

import org.dpp.tradelab.portfolio.model.PositionFill
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PositionFillRepository : JpaRepository<PositionFill, UUID> {

    fun findByUserIdAndAccountIdOrderByFilledAtAsc(
        userId: UUID,
        accountId: UUID,
        pageable: Pageable
    ): Page<PositionFill>
}
