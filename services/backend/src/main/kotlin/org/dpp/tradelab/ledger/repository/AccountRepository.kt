package org.dpp.tradelab.ledger.repository

import org.dpp.tradelab.ledger.model.Account
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID> {
    fun findAllByUserId(userId: UUID): List<Account>
}
