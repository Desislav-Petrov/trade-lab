package org.dpp.tradelab.ledger.service

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

        // First save — let Hibernate generate the UUID via @GeneratedValue.
        // Do NOT pre-assign id; doing so causes Spring Data to call merge() instead
        // of persist(), which issues a stale UPDATE before the INSERT.
        val account = accountRepository.save(
            Account(
                userId = userId,
                name = name ?: "",   // placeholder — resolved below if name is null
                currency = currency
            )
        )

        // Resolve default name only after the id has been assigned by the DB.
        val finalAccount = if (name == null) {
            accountRepository.save(account.copy(name = "account-${account.id}"))
        } else {
            account
        }

        eventPublisher.publishEvent(
            AccountOpenedEvent(
                accountId = finalAccount.id!!,
                userId = finalAccount.userId,
                currency = finalAccount.currency.name,
                timestamp = Instant.now()
            )
        )

        return finalAccount
    }

    @Transactional(readOnly = true)
    fun listAccountsByUser(userId: UUID): List<Account> =
        accountRepository.findAllByUserId(userId)
}
