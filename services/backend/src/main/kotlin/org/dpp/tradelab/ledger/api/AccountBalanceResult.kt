package org.dpp.tradelab.ledger.api

import java.math.BigDecimal

/**
 * Result containing account balance and currency.
 *
 * Returned by [LedgerApi.getBalance] for cross-domain balance queries.
 */
data class AccountBalanceResult(
    val balance: BigDecimal,
    val currency: String
)
