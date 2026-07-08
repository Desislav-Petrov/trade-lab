package org.dpp.tradelab.ledger.controller

import org.dpp.tradelab.ledger.exception.InvalidCurrencyException
import org.dpp.tradelab.ledger.generated.api.AccountsApiDelegate
import org.dpp.tradelab.ledger.generated.model.AccountListResponse
import org.dpp.tradelab.ledger.generated.model.AccountResponse
import org.dpp.tradelab.ledger.generated.model.OpenAccountRequest
import org.dpp.tradelab.ledger.generated.model.TopUpAccountRequest
import org.dpp.tradelab.ledger.generated.model.TopUpAccountResponse
import org.dpp.tradelab.ledger.generated.model.TransactionListResponse
import org.dpp.tradelab.ledger.generated.model.TransactionResponse
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.AccountStatus
import org.dpp.tradelab.ledger.model.AssetType
import org.dpp.tradelab.ledger.model.Currency
import org.dpp.tradelab.ledger.model.EntryType
import org.dpp.tradelab.ledger.model.LedgerEntry
import org.dpp.tradelab.ledger.service.AccountService
import org.dpp.tradelab.ledger.service.LedgerService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class LedgerApiDelegateImpl(
    private val accountService: AccountService,
    private val ledgerService: LedgerService,
) : AccountsApiDelegate {

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

    override fun listAccounts(userId: UUID, status: String?): ResponseEntity<AccountListResponse> {
        val validStatuses = setOf("ACTIVE", "SUSPENDED", "CLOSED")
        if (status != null && status !in validStatuses) {
            throw IllegalArgumentException("status must be one of ${validStatuses.joinToString()}")
        }

        val accounts = when (status) {
            "ACTIVE" -> accountService.listActiveAccountsByUser(userId)
            else -> accountService.listAccountsByUser(userId)
        }
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

    override fun getAccountTransactions(
        accountId: UUID,
        userId: UUID,
        page: Int,
    ): ResponseEntity<TransactionListResponse> {
        val result = ledgerService.getTransactions(
            accountId = accountId,
            userId = userId,
            page = page,
            pageSize = 25,
        )

        val transactions = result.content.map { entry ->
            TransactionResponse(
                id = entry.entryId,
                type = when (entry.type) {
                    EntryType.CREDIT -> TransactionResponse.Type.CREDIT
                    EntryType.DEBIT  -> TransactionResponse.Type.DEBIT
                },
                assetType = when (entry.assetType) {
                    AssetType.CASH       -> TransactionResponse.AssetType.CASH
                    AssetType.STOCK_BUY  -> TransactionResponse.AssetType.STOCK_BUY
                    AssetType.STOCK_SELL -> TransactionResponse.AssetType.STOCK_SELL
                },
                amount      = entry.amount,
                currency    = entry.currency,
                ticker      = entry.ticker,
                shares      = entry.shares,
                description = entry.description,
                createdAt   = OffsetDateTime.ofInstant(entry.createdAt!!, ZoneOffset.UTC),
            )
        }

        return ResponseEntity.ok(
            TransactionListResponse(
                transactions = transactions,
                page         = result.number,
                totalPages   = result.totalPages,
                totalCount   = result.totalElements.toInt(),
            )
        )
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
                AccountStatus.ACTIVE    -> AccountResponse.Status.ACTIVE
                AccountStatus.SUSPENDED -> AccountResponse.Status.SUSPENDED
                AccountStatus.CLOSED    -> AccountResponse.Status.CLOSED
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
