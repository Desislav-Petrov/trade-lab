package org.dpp.tradelab.ledger.repository

import jakarta.persistence.LockModeType
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.AccountStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface AccountRepository : JpaRepository<Account, UUID> {
    fun findAllByUserId(userId: UUID): List<Account>
    fun findAllByUserIdAndStatus(userId: UUID, status: AccountStatus): List<Account>

    /**
     * Loads an account under a pessimistic write lock (SELECT ... FOR UPDATE) so that
     * concurrent balance mutations on the same account are serialized. The lock is held
     * until the surrounding transaction commits. See decision
     * 2026-10-26-account-balance-pessimistic-locking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountId = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): Optional<Account>
}
