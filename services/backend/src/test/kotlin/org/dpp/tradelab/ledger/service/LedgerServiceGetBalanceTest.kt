package org.dpp.tradelab.ledger.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.ledger.api.AccountBalanceResult
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.Currency
import org.dpp.tradelab.ledger.repository.AccountRepository
import org.dpp.tradelab.ledger.repository.LedgerEntryRepository
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class LedgerServiceGetBalanceTest : FunSpec({

    val accountRepository = mock<AccountRepository>()
    val ledgerEntryRepository = mock<LedgerEntryRepository>()
    val ledgerService = LedgerService(accountRepository, ledgerEntryRepository)

    val accountId = UUID.randomUUID()
    val userId = UUID.randomUUID()

    test("getBalance_validAccount_returnsCorrectBalanceAndCurrency") {
        val balance = BigDecimal("1500.50")
        val account = Account(
            accountId = accountId,
            userId = userId,
            name = "Test Account",
            balance = balance,
            currency = Currency.USD
        )

        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))

        val result = ledgerService.getBalance(accountId)

        result shouldBe AccountBalanceResult(
            balance = balance,
            currency = "USD"
        )
    }

    test("getBalance_accountNotFound_throwsAccountNotFoundException") {
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.empty())

        shouldThrow<AccountNotFoundException> {
            ledgerService.getBalance(accountId)
        }
    }
})
