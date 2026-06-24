package org.dpp.tradelab.ledger.repository

import org.dpp.tradelab.ledger.model.LedgerEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID>
