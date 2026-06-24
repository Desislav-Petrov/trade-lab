package org.dpp.tradelab.ledger.messaging

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class AccountToppedUpEvent(
    val accountId: UUID,
    val userId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val newBalance: BigDecimal,
    val ledgerEntryId: UUID,
    val timestamp: Instant
)
