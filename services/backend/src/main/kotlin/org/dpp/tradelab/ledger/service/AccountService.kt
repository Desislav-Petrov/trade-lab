package org.dpp.tradelab.ledger.service

import org.dpp.tradelab.ledger.exception.InvalidCurrencyException
import org.dpp.tradelab.ledger.exception.UserNotFoundException
import org.dpp.tradelab.ledger.messaging.AccountOpenedEvent
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.Currency
import org.dpp.tradelab.ledger.repository.AccountRepository
import org.dpp.tradelab.user.api.UserLookupApi
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val userLookupApi: UserLookupApi,
    private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional
    fun openAccount(userId: UUID, currency: Currency, name: String?): Account {
        if (!userLookupApi.existsById(userId)) {
            throw UserNotFoundException(userId)
        }

        val id = UUID.randomUUID()
        val resolvedName = name ?: "account-$id"

        val account = accountRepository.save(
            Account(
                id = id,
                userId = userId,
                name = resolvedName,
                currency = currency
            )
        )

        eventPublisher.publishEvent(
            AccountOpenedEvent(
                accountId = account.id!!,
                userId = account.userId,
                currency = account.currency.name,
                timestamp = Instant.now()
            )
        )

        return account
    }

    @Transactional(readOnly = true)
    fun listAccountsByUser(userId: UUID): List<Account> =
        accountRepository.findAllByUserId(userId)
}
