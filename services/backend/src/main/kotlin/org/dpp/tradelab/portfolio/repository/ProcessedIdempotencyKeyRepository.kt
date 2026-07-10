package org.dpp.tradelab.portfolio.repository

import org.dpp.tradelab.portfolio.model.ProcessedIdempotencyKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProcessedIdempotencyKeyRepository : JpaRepository<ProcessedIdempotencyKey, UUID> {

    fun existsByIdempotencyKey(idempotencyKey: UUID): Boolean
}
