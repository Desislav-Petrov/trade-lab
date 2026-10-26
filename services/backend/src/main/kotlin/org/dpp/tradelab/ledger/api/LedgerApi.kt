package org.dpp.tradelab.ledger.api

import java.math.BigDecimal
import java.util.UUID

/**
 * Cross-domain synchronous interface for recording ledger transactions.
 *
 * Exposed by the Ledger domain to be consumed by other domains (e.g. Stock Trading).
 * Consumers import only this interface — never anything from ledger.model, ledger.service,
 * or ledger.repository.
 */
interface LedgerApi {
    fun recordTransaction(
        accountId: UUID,
        userId: UUID,
        type: TransactionType,
        assetType: TransactionAssetType,
        amount: BigDecimal,
        currency: String,
        ticker: String?,
        description: String?
    )

    /**
     * Get the balance and currency for a specific account.
     *
     * @param accountId The unique identifier of the account
     * @return AccountBalanceResult containing balance and currency
     * @throws AccountNotFoundException if the account does not exist
     */
    fun getBalance(accountId: UUID): AccountBalanceResult

    /**
     * Settle a stock purchase atomically within the Ledger domain.
     *
     * The Ledger loads the account under a pessimistic write lock as it starts writing,
     * performs the fund check under that lock against committed state, and either:
     *  - writes the `DEBIT / CASH` and `CREDIT / STOCK_BUY` entries and debits the balance,
     *    returning [StockBuyResult.COMPLETED]; or
     *  - writes nothing and returns [StockBuyResult.INSUFFICIENT_FUNDS].
     *
     * No balance or locking concern is exposed to the caller. The lock is held until the
     * surrounding transaction commits, serializing concurrent buys/top-ups on the account.
     * See decision 2026-10-26-account-balance-pessimistic-locking and
     * 2026-07-08-stock-trading-ledger-sync-api.
     *
     * @throws AccountNotFoundException if the account does not exist
     */
    fun recordStockBuy(
        accountId: UUID,
        userId: UUID,
        ticker: String,
        quantity: BigDecimal,
        executionPrice: BigDecimal,
        currency: String
    ): StockBuyResult
}
