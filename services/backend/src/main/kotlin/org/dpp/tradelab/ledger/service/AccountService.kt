package org.dpp.tradelab.ledger.service

import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.ledger.exception.UserNotFoundException
import org.dpp.tradelab.ledger.messaging.AccountOpenedEvent
import org.dpp.tradelab.ledger.messaging.AccountToppedUpEvent
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.AssetType
import org.dpp.tradelab.ledger.model.Currency
import org.dpp.tradelab.ledger.model.EntryType
import org.dpp.tradelab.ledger.model.LedgerEntry
import org.dpp.tradelab.ledger.repository.AccountRepository
import org.dpp.tradelab.ledger.repository.LedgerEntryRepository
import org.dpp.tradelab.user.api.UserLookupApi
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val userLookupApi: UserLookupApi,
    private val eventPublisher: ApplicationEventPublisher,
    private val topUpValidator: AccountTopUpValidator
) {

    @Transactional
    fun openAccount(userId: UUID, currency: Currency, name: String?): Account {
        if (!userLookupApi.existsById(userId)) {
            throw UserNotFoundException(userId)
        }

        val id = UUID.randomUUID()
        val resolvedName = name ?: id.toString()

        val account = accountRepository.save(
            Account(
                accountId = id,
                userId = userId,
                name = resolvedName,
                currency = currency
            )
        )

        eventPublisher.publishEvent(
            AccountOpenedEvent(
                accountId = account.accountId,
                userId = account.userId,
                currency = account.currency.name,
                timestamp = Instant.now()
            )
        )

        return account
    }

    @Transactional
    fun topUpAccount(accountId: UUID, userId: UUID, amount: BigDecimal): Pair<Account, LedgerEntry> {
        topUpValidator.validateAmount(amount)
        val account = accountRepository.findById(accountId).orElseThrow { AccountNotFoundException(accountId) }
        topUpValidator.validateAccountEligibility(account, userId)
        // Update balance
        account.balance = account.balance.add(amount)
        val savedAccount = accountRepository.save(account)
        // Create ledger entry
        val entryId = UUID.randomUUID()
        val entry = ledgerEntryRepository.save(
            LedgerEntry(
                entryId = entryId,
                accountId = account.accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = amount,
                currency = account.currency.name,
                description = "Top-up"
            )
        )
        // Publish event
        eventPublisher.publishEvent(
            AccountToppedUpEvent(
                accountId = account.accountId,
                userId = account.userId,
                amount = amount,
                currency = account.currency.name,
                newBalance = savedAccount.balance,
                ledgerEntryId = entry.entryId,
                timestamp = Instant.now()
            )
        )
        // Return saved account and ledger entry
        return Pair(savedAccount, entry)
    }

    @Transactional(readOnly = true)
    fun listAccountsByUser(userId: UUID): List<Account> =
        accountRepository.findAllByUserId(userId)
}
