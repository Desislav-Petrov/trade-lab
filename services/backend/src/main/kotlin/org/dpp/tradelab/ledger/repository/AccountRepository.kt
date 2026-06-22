package org.dpp.tradelab.ledger.repository

import org.dpp.tradelab.ledger.model.Account
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AccountRepository : JpaRepository<Account, UUID> {
    fun findAllByUserId(userId: UUID): List<Account>
}
