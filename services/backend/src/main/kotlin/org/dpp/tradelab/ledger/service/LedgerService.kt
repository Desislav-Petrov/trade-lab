package org.dpp.tradelab.ledger.service

import org.dpp.tradelab.ledger.api.AccountBalanceResult
import org.dpp.tradelab.ledger.api.AccountSummary
import org.dpp.tradelab.ledger.api.LedgerAccountApi
import org.dpp.tradelab.ledger.api.LedgerApi
import org.dpp.tradelab.ledger.api.TransactionAssetType
import org.dpp.tradelab.ledger.api.TransactionType
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
        type: TransactionType,
        assetType: TransactionAssetType,
        amount: BigDecimal,
        currency: String,
        ticker: String?,
        description: String?
    ) {
        val entryType = when (type) {
            TransactionType.CREDIT -> EntryType.CREDIT
            TransactionType.DEBIT -> EntryType.DEBIT
        }

        val entryAssetType = when (assetType) {
            TransactionAssetType.CASH -> AssetType.CASH
            TransactionAssetType.STOCK_BUY -> AssetType.STOCK_BUY
            TransactionAssetType.STOCK_SELL -> AssetType.STOCK_SELL
        }

        val mutatesCashBalance = entryType == EntryType.DEBIT && entryAssetType == AssetType.CASH

        // A cash debit mutates the stored balance: load the account under a pessimistic
        // write lock so concurrent balance mutations on the same account are serialized and
        // the balance re-check below reflects committed state. Non-mutating entry types only
        // need an existence check. See decision 2026-10-26-account-balance-pessimistic-locking.
        val account = if (mutatesCashBalance) {
            accountRepository.findByIdForUpdate(accountId)
                .orElseThrow { AccountNotFoundException(accountId) }
        } else {
            accountRepository.findById(accountId)
                .orElseThrow { AccountNotFoundException(accountId) }
        }

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

        if (mutatesCashBalance) {
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

    @Transactional(readOnly = true)
    override fun getBalance(accountId: UUID): AccountBalanceResult {
        val account = accountRepository.findById(accountId)
            .orElseThrow { AccountNotFoundException(accountId) }

        return AccountBalanceResult(
            balance = account.balance,
            currency = account.currency.name
        )
    }

    @Transactional
    override fun getBalanceForUpdate(accountId: UUID): AccountBalanceResult {
        val account = accountRepository.findByIdForUpdate(accountId)
            .orElseThrow { AccountNotFoundException(accountId) }

        return AccountBalanceResult(
            balance = account.balance,
            currency = account.currency.name
        )
    }
}
