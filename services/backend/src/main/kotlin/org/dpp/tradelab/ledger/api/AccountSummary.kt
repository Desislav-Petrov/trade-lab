package org.dpp.tradelab.ledger.api

import java.math.BigDecimal
import java.util.UUID

data class AccountSummary(
    val id: UUID,
    val userId: UUID,
    val currency: String,
    val balance: BigDecimal,
    val status: String
)
