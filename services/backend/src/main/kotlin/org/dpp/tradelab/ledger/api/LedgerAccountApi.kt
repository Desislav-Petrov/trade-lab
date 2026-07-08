package org.dpp.tradelab.ledger.api

import java.util.UUID

/**
 * Cross-domain synchronous interface for querying account information.
 *
 * Exposed by the Ledger domain to be consumed by other domains (e.g. Stock Trading).
 * The returned [AccountSummary] is a plain data class with no JPA entity references.
 */
interface LedgerAccountApi {
    fun getAccount(accountId: UUID): AccountSummary
}
