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
}
