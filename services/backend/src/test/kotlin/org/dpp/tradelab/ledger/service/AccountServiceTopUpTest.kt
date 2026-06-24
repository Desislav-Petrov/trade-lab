package org.dpp.tradelab.ledger.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.ledger.exception.AccountNotActiveException
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class AccountServiceTopUpTest : FunSpec({

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

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    test("topUpAccount_validInput_balanceIncreasesCorrectly") {
        val initialBalance = BigDecimal("500.0000")
        val amount = BigDecimal("200")
        val account = Account(
            accountId = accountId,
            userId = userId,
            name = "Test Account",
            balance = initialBalance,
            currency = Currency.USD,
            status = AccountStatus.ACTIVE
        )
        val savedAccount = Account(
            accountId = accountId,
            userId = userId,
            name = "Test Account",
            balance = BigDecimal("700.0000"),
            currency = Currency.USD,
            status = AccountStatus.ACTIVE
        )
        val savedEntry = LedgerEntry(
            entryId = UUID.randomUUID(),
            accountId = accountId,
            type = EntryType.CREDIT,
            assetType = AssetType.CASH,
            amount = amount,
            currency = "USD",
            description = "Top-up"
        )
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenReturn(savedAccount)
        whenever(ledgerEntryRepository.save(any())).thenReturn(savedEntry)

        val result = accountService.topUpAccount(accountId, userId, amount)

        result.balance shouldBe BigDecimal("700.0000")
    }

    test("topUpAccount_validInput_savesLedgerEntryWithCorrectFields") {
        val amount = BigDecimal("300")
        val account = Account(
            accountId = accountId,
            userId = userId,
            name = "Test Account",
            balance = BigDecimal.ZERO,
            currency = Currency.GBP,
            status = AccountStatus.ACTIVE
        )
        val savedAccount = Account(
            accountId = accountId,
            userId = userId,
            name = "Test Account",
            balance = amount,
            currency = Currency.GBP,
            status = AccountStatus.ACTIVE
        )
        val savedEntry = LedgerEntry(
            entryId = UUID.randomUUID(),
            accountId = accountId,
            type = EntryType.CREDIT,
            assetType = AssetType.CASH,
            amount = amount,
            currency = "GBP",
            description = "Top-up"
        )
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenReturn(savedAccount)
        whenever(ledgerEntryRepository.save(any())).thenReturn(savedEntry)

        accountService.topUpAccount(accountId, userId, amount)

        val captor = argumentCaptor<LedgerEntry>()
        verify(ledgerEntryRepository).save(captor.capture())
        val entry = captor.firstValue
        entry.accountId shouldBe accountId
        entry.type shouldBe EntryType.CREDIT
        entry.assetType shouldBe AssetType.CASH
        entry.amount shouldBe amount
        entry.currency shouldBe "GBP"
        entry.description shouldBe "Top-up"
    }

    test("topUpAccount_validInput_publishesAccountToppedUpEventWithAllSevenFields") {
        val amount = BigDecimal("1000")
        val newBalance = BigDecimal("1000.0000")
        val entryId = UUID.randomUUID()
        val account = Account(
            accountId = accountId,
            userId = userId,
            name = "Test Account",
            balance = BigDecimal.ZERO,
            currency = Currency.EUR,
            status = AccountStatus.ACTIVE
        )
        val savedAccount = Account(
            accountId = accountId,
            userId = userId,
            name = "Test Account",
            balance = newBalance,
            currency = Currency.EUR,
            status = AccountStatus.ACTIVE
        )
        val savedEntry = LedgerEntry(
            entryId = entryId,
            accountId = accountId,
            type = EntryType.CREDIT,
            assetType = AssetType.CASH,
            amount = amount,
            currency = "EUR",
            description = "Top-up"
        )
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenReturn(savedAccount)
        whenever(ledgerEntryRepository.save(any())).thenReturn(savedEntry)

        accountService.topUpAccount(accountId, userId, amount)

        val captor = argumentCaptor<AccountToppedUpEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        val event = captor.firstValue
        event.accountId shouldBe accountId
        event.userId shouldBe userId
        event.amount shouldBe amount
        event.currency shouldBe "EUR"
        event.newBalance shouldBe newBalance
        event.ledgerEntryId shouldBe entryId
        event.timestamp shouldNotBe null
    }

    test("topUpAccount_validInput_returnsUpdatedAccount") {
        val amount = BigDecimal("500")
        val account = Account(
            accountId = accountId,
            userId = userId,
            name = "Test Account",
            balance = BigDecimal.ZERO,
            currency = Currency.USD,
            status = AccountStatus.ACTIVE
        )
        val savedAccount = Account(
            accountId = accountId,
            userId = userId,
            name = "Test Account",
            balance = amount,
            currency = Currency.USD,
            status = AccountStatus.ACTIVE
        )
        val savedEntry = LedgerEntry(
            entryId = UUID.randomUUID(),
            accountId = accountId,
            type = EntryType.CREDIT,
            assetType = AssetType.CASH,
            amount = amount,
            currency = "USD",
            description = "Top-up"
        )
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenReturn(savedAccount)
        whenever(ledgerEntryRepository.save(any())).thenReturn(savedEntry)

        val result = accountService.topUpAccount(accountId, userId, amount)

        result shouldBe savedAccount
    }

    // -------------------------------------------------------------------------
    // Amount validation
    // -------------------------------------------------------------------------

    test("topUpAccount_amountIsZero_throwsIllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            accountService.topUpAccount(accountId, userId, BigDecimal.ZERO)
        }

        verify(accountRepository, never()).findById(any())
        verify(accountRepository, never()).save(any())
        verify(ledgerEntryRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    test("topUpAccount_amountIsNegative_throwsIllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            accountService.topUpAccount(accountId, userId, BigDecimal("-1"))
        }

        verify(accountRepository, never()).findById(any())
        verify(accountRepository, never()).save(any())
        verify(ledgerEntryRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    test("topUpAccount_amountHasFractionalPart_throwsIllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            accountService.topUpAccount(accountId, userId, BigDecimal("1.5"))
        }

        verify(accountRepository, never()).findById(any())
        verify(accountRepository, never()).save(any())
        verify(ledgerEntryRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    test("topUpAccount_amountExceedsMaximum_throwsIllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            accountService.topUpAccount(accountId, userId, BigDecimal("10000001"))
        }

        verify(accountRepository, never()).findById(any())
        verify(accountRepository, never()).save(any())
        verify(ledgerEntryRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    // -------------------------------------------------------------------------
    // Account lookup and guard checks
    // -------------------------------------------------------------------------

    test("topUpAccount_accountNotFound_throwsAccountNotFoundException") {
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.empty())

        shouldThrow<AccountNotFoundException> {
            accountService.topUpAccount(accountId, userId, BigDecimal("100"))
        }

        verify(accountRepository, never()).save(any())
        verify(ledgerEntryRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    test("topUpAccount_ownershipMismatch_throwsAccountNotActiveException") {
        val differentUserId = UUID.randomUUID()
        val account = Account(
            accountId = accountId,
            userId = differentUserId,
            name = "Test Account",
            balance = BigDecimal.ZERO,
            currency = Currency.USD,
            status = AccountStatus.ACTIVE
        )
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))

        shouldThrow<AccountNotActiveException> {
            accountService.topUpAccount(accountId, userId, BigDecimal("100"))
        }

        verify(accountRepository, never()).save(any())
        verify(ledgerEntryRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    test("topUpAccount_accountStatusSuspended_throwsAccountNotActiveException") {
        val account = Account(
            accountId = accountId,
            userId = userId,
            name = "Test Account",
            balance = BigDecimal.ZERO,
            currency = Currency.USD,
            status = AccountStatus.SUSPENDED
        )
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))

        shouldThrow<AccountNotActiveException> {
            accountService.topUpAccount(accountId, userId, BigDecimal("100"))
        }

        verify(accountRepository, never()).save(any())
        verify(ledgerEntryRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    test("topUpAccount_accountStatusClosed_throwsAccountNotActiveException") {
        val account = Account(
            accountId = accountId,
            userId = userId,
            name = "Test Account",
            balance = BigDecimal.ZERO,
            currency = Currency.USD,
            status = AccountStatus.CLOSED
        )
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))

        shouldThrow<AccountNotActiveException> {
            accountService.topUpAccount(accountId, userId, BigDecimal("100"))
        }

        verify(accountRepository, never()).save(any())
        verify(ledgerEntryRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }
})
