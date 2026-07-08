package org.dpp.tradelab.ledger.api

import java.math.BigDecimal
import java.util.UUID

/**
 * A lightweight view of an Account, safe to cross domain boundaries.
 * Contains no JPA entity references.
 */
data class AccountSummary(
    val id: UUID,
    val userId: UUID,
    val currency: String,
    val balance: BigDecimal,
    val status: String
)

/**
 * Cross-domain synchronous interface for querying account information.
 *
 * Exposed by the Ledger domain to be consumed by other domains (e.g. Stock Trading).
 * The returned [AccountSummary] is a plain data class with no JPA entity references.
 */
interface LedgerAccountApi {
    fun getAccount(accountId: UUID): AccountSummary
}
