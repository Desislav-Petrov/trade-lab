package org.dpp.tradelab.ledger.repository

import org.dpp.tradelab.ledger.model.LedgerEntry
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID> {

    fun findByAccountId(accountId: UUID, pageable: Pageable): Page<LedgerEntry>
}
