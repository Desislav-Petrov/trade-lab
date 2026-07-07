package org.dpp.tradelab.ledger.service

import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.ledger.exception.AccountOwnershipException
import org.dpp.tradelab.ledger.model.LedgerEntry
import org.dpp.tradelab.ledger.repository.AccountRepository
import org.dpp.tradelab.ledger.repository.LedgerEntryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class LedgerService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) {

    @Transactional(readOnly = true)
    fun getTransactions(accountId: UUID, userId: UUID, page: Int, pageSize: Int): Page<LedgerEntry> {
        val account = accountRepository.findById(accountId)
            .orElseThrow { AccountNotFoundException(accountId) }

        if (account.userId != userId) {
            throw AccountOwnershipException(accountId)
        }

        return ledgerEntryRepository.findByAccountId(
            accountId,
            PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        )
    }
}
