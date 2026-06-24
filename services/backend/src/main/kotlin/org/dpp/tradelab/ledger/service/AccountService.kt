package org.dpp.tradelab.ledger.service

import org.dpp.tradelab.ledger.exception.AccountNotActiveException
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.ledger.exception.UserNotFoundException
import org.dpp.tradelab.ledger.messaging.AccountOpenedEvent
import org.dpp.tradelab.ledger.messaging.AccountToppedUpEvent
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.AccountStatus
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
    private val eventPublisher: ApplicationEventPublisher
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
    fun topUpAccount(accountId: UUID, userId: UUID, amount: BigDecimal): Account {
        // 1. Validate amount > 0
        if (amount <= BigDecimal.ZERO) throw IllegalArgumentException("amount must be greater than zero")
        // 2. Validate whole number (stripTrailingZeros().scale() <= 0)
        if (amount.stripTrailingZeros().scale() > 0) throw IllegalArgumentException("amount must be a whole number")
        // 3. Validate amount <= 10_000_000
        if (amount > BigDecimal(10_000_000)) throw IllegalArgumentException("amount must not exceed 10,000,000")
        // 4. Load account
        val account = accountRepository.findById(accountId).orElseThrow { AccountNotFoundException(accountId) }
        // 5. Validate ownership
        if (account.userId != userId) throw AccountNotActiveException(accountId)
        // 6. Validate status
        if (account.status != AccountStatus.ACTIVE) throw AccountNotActiveException(accountId)
        // 7. Update balance
        account.balance = account.balance.add(amount)
        val savedAccount = accountRepository.save(account)
        // 8. Create ledger entry
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
        // 9. Publish event
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
        // 10. Return saved account
        return savedAccount
    }

    @Transactional(readOnly = true)
    fun listAccountsByUser(userId: UUID): List<Account> =
        accountRepository.findAllByUserId(userId)
}
