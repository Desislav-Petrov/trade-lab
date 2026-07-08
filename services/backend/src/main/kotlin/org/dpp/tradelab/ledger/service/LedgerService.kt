package org.dpp.tradelab.ledger.service

import org.dpp.tradelab.ledger.api.AccountSummary
import org.dpp.tradelab.ledger.api.LedgerAccountApi
import org.dpp.tradelab.ledger.api.LedgerApi
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.ledger.exception.AccountOwnershipException
import org.dpp.tradelab.ledger.model.AssetType
import org.dpp.tradelab.ledger.model.EntryType
import org.dpp.tradelab.ledger.model.LedgerEntry
import org.dpp.tradelab.ledger.repository.AccountRepository
import org.dpp.tradelab.ledger.repository.LedgerEntryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class LedgerService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) : LedgerApi, LedgerAccountApi {

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

    @Transactional
    override fun recordTransaction(
        accountId: UUID,
        userId: UUID,
        type: String,
        assetType: String,
        amount: BigDecimal,
        currency: String,
        ticker: String?,
        description: String?
    ) {
        val entryType = runCatching { EntryType.valueOf(type) }
            .getOrElse { throw IllegalArgumentException("Unknown entry type: $type. Must be one of ${EntryType.entries.joinToString()}") }

        val entryAssetType = runCatching { AssetType.valueOf(assetType) }
            .getOrElse { throw IllegalArgumentException("Unknown asset type: $assetType. Must be one of ${AssetType.entries.joinToString()}") }

        val account = accountRepository.findById(accountId)
            .orElseThrow { AccountNotFoundException(accountId) }

        val entry = LedgerEntry(
            entryId = UUID.randomUUID(),
            accountId = accountId,
            type = entryType,
            assetType = entryAssetType,
            amount = amount,
            currency = currency,
            ticker = ticker,
            description = description
        )
        ledgerEntryRepository.save(entry)

        if (entryType == EntryType.DEBIT && entryAssetType == AssetType.CASH) {
            val newBalance = account.balance.subtract(amount)
            if (newBalance < BigDecimal.ZERO) {
                throw IllegalStateException(
                    "Deducting $amount from account $accountId would result in a negative balance. " +
                        "Current balance: ${account.balance}"
                )
            }
            account.balance = newBalance
            accountRepository.save(account)
        }
    }

    @Transactional(readOnly = true)
    override fun getAccount(accountId: UUID): AccountSummary {
        val account = accountRepository.findById(accountId)
            .orElseThrow { AccountNotFoundException(accountId) }

        return AccountSummary(
            id = account.accountId,
            userId = account.userId,
            currency = account.currency.name,
            balance = account.balance,
            status = account.status.name.lowercase()
        )
    }
}
