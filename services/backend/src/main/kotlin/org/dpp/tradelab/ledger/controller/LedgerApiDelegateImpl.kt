package org.dpp.tradelab.ledger.controller

import org.dpp.tradelab.ledger.exception.InvalidCurrencyException
import org.dpp.tradelab.ledger.generated.api.AccountsApiDelegate
import org.dpp.tradelab.ledger.generated.model.AccountListResponse
import org.dpp.tradelab.ledger.generated.model.AccountResponse
import org.dpp.tradelab.ledger.generated.model.OpenAccountRequest
import org.dpp.tradelab.ledger.generated.model.TopUpAccountRequest
import org.dpp.tradelab.ledger.generated.model.TopUpAccountResponse
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.AccountStatus
import org.dpp.tradelab.ledger.model.Currency
import org.dpp.tradelab.ledger.model.LedgerEntry
import org.dpp.tradelab.ledger.service.AccountService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class LedgerApiDelegateImpl(private val accountService: AccountService) : AccountsApiDelegate {

    override fun openAccount(openAccountRequest: OpenAccountRequest): ResponseEntity<AccountResponse> {
        val currency = try {
            Currency.valueOf(openAccountRequest.currency.value)
        } catch (e: IllegalArgumentException) {
            throw InvalidCurrencyException(openAccountRequest.currency.value)
        }

        val account = accountService.openAccount(
            userId = openAccountRequest.userId,
            currency = currency,
            name = openAccountRequest.name
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(account.toResponse())
    }

    override fun listAccounts(userId: UUID): ResponseEntity<AccountListResponse> {
        val accounts = accountService.listAccountsByUser(userId)
        return ResponseEntity.ok(AccountListResponse(accounts = accounts.map { it.toResponse() }))
    }

    override fun topUpAccount(
        accountId: UUID,
        topUpAccountRequest: TopUpAccountRequest
    ): ResponseEntity<TopUpAccountResponse> {
        val (account, entry) = accountService.topUpAccount(
            accountId = accountId,
            userId = topUpAccountRequest.userId,
            amount = topUpAccountRequest.amount.toBigDecimal()
        )
        return ResponseEntity.ok(buildTopUpResponse(account, entry))
    }

    private fun Account.toResponse(): AccountResponse =
        AccountResponse(
            id = accountId,
            userId = userId,
            name = name,
            balance = balance,
            currency = when (currency) {
                Currency.USD -> AccountResponse.Currency.USD
                Currency.GBP -> AccountResponse.Currency.GBP
                Currency.EUR -> AccountResponse.Currency.EUR
            },
            status = when (status) {
                AccountStatus.ACTIVE -> AccountResponse.Status.ACTIVE
                AccountStatus.SUSPENDED -> AccountResponse.Status.SUSPENDED
                AccountStatus.CLOSED -> AccountResponse.Status.CLOSED
            },
            createdAt = OffsetDateTime.ofInstant(createdAt!!, ZoneOffset.UTC)
        )

    private fun buildTopUpResponse(account: Account, entry: LedgerEntry): TopUpAccountResponse =
        TopUpAccountResponse(
            accountId = account.accountId,
            newBalance = account.balance,
            currency = when (account.currency) {
                Currency.USD -> TopUpAccountResponse.Currency.USD
                Currency.GBP -> TopUpAccountResponse.Currency.GBP
                Currency.EUR -> TopUpAccountResponse.Currency.EUR
            },
            ledgerEntryId = entry.entryId,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC)
        )
}
