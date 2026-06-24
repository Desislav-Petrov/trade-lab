package org.dpp.tradelab.ledger.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.ledger.exception.UserNotFoundException
import org.dpp.tradelab.ledger.messaging.AccountOpenedEvent
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.AccountStatus
import org.dpp.tradelab.ledger.model.Currency
import org.dpp.tradelab.ledger.repository.AccountRepository
import org.dpp.tradelab.ledger.repository.LedgerEntryRepository
import org.dpp.tradelab.user.api.UserLookupApi
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.util.UUID

class AccountServiceTest : FunSpec({

    val accountRepository = mock<AccountRepository>()
    val ledgerEntryRepository = mock<LedgerEntryRepository>()
    val userLookupApi = mock<UserLookupApi>()
    val eventPublisher = mock<ApplicationEventPublisher>()
    val accountService = AccountService(accountRepository, ledgerEntryRepository, userLookupApi, eventPublisher)

    val userId = UUID.randomUUID()
    val accountId = UUID.randomUUID()

    beforeEach {
        reset(accountRepository, ledgerEntryRepository, userLookupApi, eventPublisher)
    }

    test("openAccount_unknownUserId_throwsUserNotFoundException") {
        whenever(userLookupApi.existsById(userId)).thenReturn(false)

        shouldThrow<UserNotFoundException> {
            accountService.openAccount(userId, Currency.USD, null)
        }

        verify(accountRepository, never()).save(any())
    }

    test("openAccount_validInput_persistsAccountWithActiveStatusAndZeroBalance") {
        whenever(userLookupApi.existsById(userId)).thenReturn(true)
        val savedAccount = Account(
            accountId = accountId, userId = userId, name = "My Account",
            balance = BigDecimal.ZERO, currency = Currency.USD, status = AccountStatus.ACTIVE
        )
        whenever(accountRepository.save(any())).thenReturn(savedAccount)

        accountService.openAccount(userId, Currency.USD, "My Account")

        val captor = argumentCaptor<Account>()
        verify(accountRepository).save(captor.capture())
        captor.firstValue.userId shouldBe userId
        captor.firstValue.currency shouldBe Currency.USD
        captor.firstValue.status shouldBe AccountStatus.ACTIVE
        captor.firstValue.balance shouldBe BigDecimal.ZERO
        captor.firstValue.name shouldBe "My Account"
    }

    test("openAccount_nullName_usesUuidAsDefaultName") {
        whenever(userLookupApi.existsById(userId)).thenReturn(true)
        val savedAccount = Account(
            accountId = accountId, userId = userId, name = accountId.toString(),
            balance = BigDecimal.ZERO, currency = Currency.GBP, status = AccountStatus.ACTIVE
        )
        whenever(accountRepository.save(any())).thenReturn(savedAccount)

        accountService.openAccount(userId, Currency.GBP, null)

        val captor = argumentCaptor<Account>()
        verify(accountRepository).save(captor.capture())
        captor.firstValue.name shouldBe captor.firstValue.accountId.toString()
    }

    test("openAccount_validInput_returnsPersistedAccount") {
        whenever(userLookupApi.existsById(userId)).thenReturn(true)
        val savedAccount = Account(
            accountId = accountId, userId = userId, name = accountId.toString(),
            balance = BigDecimal.ZERO, currency = Currency.EUR, status = AccountStatus.ACTIVE
        )
        whenever(accountRepository.save(any())).thenReturn(savedAccount)

        val result = accountService.openAccount(userId, Currency.EUR, null)

        result shouldBe savedAccount
    }

    test("openAccount_validInput_publishesAccountOpenedEvent") {
        whenever(userLookupApi.existsById(userId)).thenReturn(true)
        val savedAccount = Account(
            accountId = accountId, userId = userId, name = "My Account",
            balance = BigDecimal.ZERO, currency = Currency.USD, status = AccountStatus.ACTIVE
        )
        whenever(accountRepository.save(any())).thenReturn(savedAccount)

        accountService.openAccount(userId, Currency.USD, "My Account")

        val captor = argumentCaptor<AccountOpenedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        captor.firstValue.accountId shouldBe accountId
        captor.firstValue.userId shouldBe userId
        captor.firstValue.currency shouldBe "USD"
        captor.firstValue.timestamp shouldNotBe null
    }

    test("listAccountsByUser_existingUserId_returnsAccounts") {
        val accounts = listOf(
            Account(accountId = UUID.randomUUID(), userId = userId, name = "Acc 1", currency = Currency.USD),
            Account(accountId = UUID.randomUUID(), userId = userId, name = "Acc 2", currency = Currency.GBP)
        )
        whenever(accountRepository.findAllByUserId(userId)).thenReturn(accounts)

        val result = accountService.listAccountsByUser(userId)

        result shouldBe accounts
    }

    test("listAccountsByUser_noAccounts_returnsEmptyList") {
        whenever(accountRepository.findAllByUserId(userId)).thenReturn(emptyList())

        val result = accountService.listAccountsByUser(userId)

        result shouldBe emptyList()
    }
})
