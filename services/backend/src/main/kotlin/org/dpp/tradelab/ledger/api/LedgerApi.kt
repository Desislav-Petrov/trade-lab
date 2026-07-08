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
}
