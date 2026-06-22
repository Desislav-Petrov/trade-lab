package org.dpp.tradelab.ledger.messaging

import java.time.Instant
import java.util.UUID

data class AccountOpenedEvent(
    val accountId: UUID,
    val userId: UUID,
    val currency: String,
    val timestamp: Instant
)
