package org.dpp.tradelab.ledger.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.ledger.exception.AccountOwnershipException
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.Currency
import org.dpp.tradelab.ledger.model.LedgerEntry
import org.dpp.tradelab.ledger.repository.AccountRepository
import org.dpp.tradelab.ledger.repository.LedgerEntryRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.util.Optional
import java.util.UUID

class LedgerServiceTest : FunSpec({

    val accountRepository = mock<AccountRepository>()
    val ledgerEntryRepository = mock<LedgerEntryRepository>()
    val ledgerService = LedgerService(accountRepository, ledgerEntryRepository)

    val userId = UUID.randomUUID()
    val accountId = UUID.randomUUID()

    test("getTransactions_validOwner_returnsPage") {
        val account = Account(
            accountId = accountId,
            userId = userId,
            name = "Test Account",
            currency = Currency.USD
        )
        val entries = listOf<LedgerEntry>()
        val expectedPage = PageImpl(entries)
        val pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "createdAt"))

        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))
        whenever(ledgerEntryRepository.findByAccountId(accountId, pageable)).thenReturn(expectedPage)

        val result = ledgerService.getTransactions(accountId, userId, 0, 25)

        result shouldBe expectedPage
        verify(accountRepository).findById(accountId)
        verify(ledgerEntryRepository).findByAccountId(accountId, pageable)
    }

    test("getTransactions_accountNotFound_throwsAccountNotFoundException") {
        whenever(accountRepository.findById(accountId)).thenReturn(Optional.empty())

        shouldThrow<AccountNotFoundException> {
            ledgerService.getTransactions(accountId, userId, 0, 25)
        }
    }

    test("getTransactions_userMismatch_throwsAccountOwnershipException") {
        val differentUserId = UUID.randomUUID()
        val account = Account(
            accountId = accountId,
            userId = differentUserId,
            name = "Other User Account",
            currency = Currency.USD
        )

        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))

        shouldThrow<AccountOwnershipException> {
            ledgerService.getTransactions(accountId, userId, 0, 25)
        }
    }
})
