package org.dpp.tradelab.ledger.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.AccountStatus
import org.dpp.tradelab.ledger.model.Currency
import org.dpp.tradelab.ledger.repository.AccountRepository
import org.dpp.tradelab.ledger.repository.LedgerEntryRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class LedgerServiceRecordTransactionTest : FunSpec({

    val accountRepository = mock<AccountRepository>()
    val ledgerEntryRepository = mock<LedgerEntryRepository>()
    val ledgerService = LedgerService(accountRepository, ledgerEntryRepository)

    val userId = UUID.randomUUID()
    val accountId = UUID.randomUUID()

    fun buildAccount(balance: BigDecimal = BigDecimal("1000.0000")) = Account(
        accountId = accountId,
        userId = userId,
        name = "Test Account",
        balance = balance,
        currency = Currency.USD,
        status = AccountStatus.ACTIVE
    )

    beforeEach {
        reset(accountRepository, ledgerEntryRepository)
    }

    // ── recordTransaction tests ──────────────────────────────────────────────

    test("recordTransaction_debitCash_happyPath_savesEntryAndDeductsBalance") {
        val account = buildAccount(BigDecimal("1000.0000"))
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenReturn(account)
        whenever(ledgerEntryRepository.save(any())).thenAnswer { it.arguments[0] }

        ledgerService.recordTransaction(
            accountId = accountId,
            userId = userId,
            type = "DEBIT",
            assetType = "CASH",
            amount = BigDecimal("200.0000"),
            currency = "USD",
            ticker = null,
            description = "Buy AAPL x2"
        )

        verify(ledgerEntryRepository).save(any())
        val accountCaptor = argumentCaptor<Account>()
        verify(accountRepository).save(accountCaptor.capture())
        accountCaptor.firstValue.balance shouldBe BigDecimal("800.0000")
    }

    test("recordTransaction_creditStockBuy_happyPath_savesEntryDoesNotChangeBalance") {
        val account = buildAccount(BigDecimal("1000.0000"))
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))
        whenever(ledgerEntryRepository.save(any())).thenAnswer { it.arguments[0] }

        ledgerService.recordTransaction(
            accountId = accountId,
            userId = userId,
            type = "CREDIT",
            assetType = "STOCK_BUY",
            amount = BigDecimal("2.0000"),
            currency = "USD",
            ticker = "AAPL",
            description = "Buy AAPL x2"
        )

        verify(ledgerEntryRepository).save(any())
        verify(accountRepository, never()).save(any())
    }

    test("recordTransaction_accountNotFound_throwsAccountNotFoundException") {
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.empty())

        shouldThrow<AccountNotFoundException> {
            ledgerService.recordTransaction(
                accountId = accountId,
                userId = userId,
                type = "DEBIT",
                assetType = "CASH",
                amount = BigDecimal("100.0000"),
                currency = "USD",
                ticker = null,
                description = null
            )
        }
    }

    test("recordTransaction_unknownEntryType_throwsIllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            ledgerService.recordTransaction(
                accountId = accountId,
                userId = userId,
                type = "TRANSFER",
                assetType = "CASH",
                amount = BigDecimal("100.0000"),
                currency = "USD",
                ticker = null,
                description = null
            )
        }
    }

    test("recordTransaction_unknownAssetType_throwsIllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            ledgerService.recordTransaction(
                accountId = accountId,
                userId = userId,
                type = "DEBIT",
                assetType = "CRYPTO",
                amount = BigDecimal("100.0000"),
                currency = "USD",
                ticker = null,
                description = null
            )
        }
    }

    test("recordTransaction_debitCashWouldGoNegative_throwsIllegalStateException") {
        val account = buildAccount(BigDecimal("50.0000"))
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))

        shouldThrow<IllegalStateException> {
            ledgerService.recordTransaction(
                accountId = accountId,
                userId = userId,
                type = "DEBIT",
                assetType = "CASH",
                amount = BigDecimal("100.0000"),
                currency = "USD",
                ticker = null,
                description = null
            )
        }
    }

    // ── getAccount tests ─────────────────────────────────────────────────────

    test("getAccount_existingAccount_returnsAccountSummary") {
        val account = buildAccount(BigDecimal("500.0000"))
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))

        val result = ledgerService.getAccount(accountId)

        result.id shouldBe accountId
        result.userId shouldBe userId
        result.currency shouldBe "USD"
        result.balance shouldBe BigDecimal("500.0000")
        result.status shouldBe "active"
    }

    test("getAccount_accountNotFound_throwsAccountNotFoundException") {
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.empty())

        shouldThrow<AccountNotFoundException> {
            ledgerService.getAccount(accountId)
        }
    }

    test("getAccount_suspendedAccount_returnsSuspendedStatus") {
        val account = Account(
            accountId = accountId,
            userId = userId,
            name = "Suspended Account",
            balance = BigDecimal("100.0000"),
            currency = Currency.GBP,
            status = AccountStatus.SUSPENDED
        )
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))

        val result = ledgerService.getAccount(accountId)

        result.status shouldBe "suspended"
        result.currency shouldBe "GBP"
    }
})
