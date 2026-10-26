package org.dpp.tradelab.ledger.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.ledger.api.StockBuyResult
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.AccountStatus
import org.dpp.tradelab.ledger.model.AssetType
import org.dpp.tradelab.ledger.model.Currency
import org.dpp.tradelab.ledger.model.EntryType
import org.dpp.tradelab.ledger.model.LedgerEntry
import org.dpp.tradelab.ledger.repository.AccountRepository
import org.dpp.tradelab.ledger.repository.LedgerEntryRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class LedgerServiceRecordStockBuyTest : FunSpec({

    val accountRepository = mock<AccountRepository>()
    val ledgerEntryRepository = mock<LedgerEntryRepository>()
    val ledgerService = LedgerService(accountRepository, ledgerEntryRepository)

    val userId = UUID.randomUUID()
    val accountId = UUID.randomUUID()
    val ticker = "AAPL"
    val quantity = BigDecimal("2.0000")
    val executionPrice = BigDecimal("182.5000")

    fun buildAccount(balance: BigDecimal) = Account(
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

    test("recordStockBuy_sufficientFunds_returnsCompleted") {
        val account = buildAccount(BigDecimal("1000.0000"))
        whenever(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(ledgerEntryRepository.save(any())).thenAnswer { it.arguments[0] }

        val result = ledgerService.recordStockBuy(accountId, userId, ticker, quantity, executionPrice, "USD")

        result shouldBe StockBuyResult.COMPLETED
    }

    test("recordStockBuy_sufficientFunds_loadsAccountUnderPessimisticLock") {
        val account = buildAccount(BigDecimal("1000.0000"))
        whenever(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(ledgerEntryRepository.save(any())).thenAnswer { it.arguments[0] }

        ledgerService.recordStockBuy(accountId, userId, ticker, quantity, executionPrice, "USD")

        verify(accountRepository).findByIdForUpdate(accountId)
        verify(accountRepository, never()).findById(any())
    }

    test("recordStockBuy_sufficientFunds_writesDebitCashAndCreditStockEntries") {
        val account = buildAccount(BigDecimal("1000.0000"))
        whenever(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(ledgerEntryRepository.save(any())).thenAnswer { it.arguments[0] }

        ledgerService.recordStockBuy(accountId, userId, ticker, quantity, executionPrice, "USD")

        val captor = argumentCaptor<LedgerEntry>()
        verify(ledgerEntryRepository, times(2)).save(captor.capture())

        val debit = captor.firstValue
        debit.type shouldBe EntryType.DEBIT
        debit.assetType shouldBe AssetType.CASH
        debit.amount shouldBe quantity.multiply(executionPrice)
        debit.ticker shouldBe null

        val credit = captor.secondValue
        credit.type shouldBe EntryType.CREDIT
        credit.assetType shouldBe AssetType.STOCK_BUY
        credit.amount shouldBe quantity
        credit.ticker shouldBe ticker
    }

    test("recordStockBuy_sufficientFunds_debitsAccountBalance") {
        val account = buildAccount(BigDecimal("1000.0000"))
        whenever(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(ledgerEntryRepository.save(any())).thenAnswer { it.arguments[0] }

        ledgerService.recordStockBuy(accountId, userId, ticker, quantity, executionPrice, "USD")

        val captor = argumentCaptor<Account>()
        verify(accountRepository).save(captor.capture())
        captor.firstValue.balance shouldBe BigDecimal("1000.0000").subtract(quantity.multiply(executionPrice))
    }

    test("recordStockBuy_insufficientFunds_returnsInsufficientFundsAndWritesNothing") {
        val account = buildAccount(BigDecimal("10.0000"))
        whenever(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account))

        val result = ledgerService.recordStockBuy(accountId, userId, ticker, quantity, executionPrice, "USD")

        result shouldBe StockBuyResult.INSUFFICIENT_FUNDS
        verify(ledgerEntryRepository, never()).save(any())
        verify(accountRepository, never()).save(any())
    }

    test("recordStockBuy_exactFunds_returnsCompleted") {
        val requiredCash = quantity.multiply(executionPrice)
        val account = buildAccount(requiredCash)
        whenever(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(ledgerEntryRepository.save(any())).thenAnswer { it.arguments[0] }

        val result = ledgerService.recordStockBuy(accountId, userId, ticker, quantity, executionPrice, "USD")

        result shouldBe StockBuyResult.COMPLETED
    }

    test("recordStockBuy_accountNotFound_throwsAccountNotFoundException") {
        whenever(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.empty())

        shouldThrow<AccountNotFoundException> {
            ledgerService.recordStockBuy(accountId, userId, ticker, quantity, executionPrice, "USD")
        }
    }
})
