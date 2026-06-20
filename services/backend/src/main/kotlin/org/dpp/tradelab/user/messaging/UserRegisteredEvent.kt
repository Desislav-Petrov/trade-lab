package org.dpp.tradelab.user.messaging

import java.time.Instant
import java.util.UUID

data class UserRegisteredEvent(
    val userId: UUID,
    val email: String,
    val timestamp: Instant
)
