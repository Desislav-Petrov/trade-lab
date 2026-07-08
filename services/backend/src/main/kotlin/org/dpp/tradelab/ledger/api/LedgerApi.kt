package org.dpp.tradelab.ledger.api

import java.math.BigDecimal
import java.util.UUID

/**
 * Cross-domain synchronous interface for recording ledger transactions.
 *
 * Exposed by the Ledger domain to be consumed by other domains (e.g. Stock Trading).
 * Consumers import only this interface — never anything from ledger.model, ledger.service,
 * or ledger.repository.
 *
 * [type] must be one of "DEBIT" or "CREDIT".
 * [assetType] must be one of "CASH", "STOCK_BUY", or "STOCK_SELL".
 */
interface LedgerApi {
    fun recordTransaction(
        accountId: UUID,
        userId: UUID,
        type: String,
        assetType: String,
        amount: BigDecimal,
        currency: String,
        ticker: String?,
        description: String?
    )
}
