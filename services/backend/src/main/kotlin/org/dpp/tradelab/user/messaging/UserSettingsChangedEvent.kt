package org.dpp.tradelab.user.messaging

import org.dpp.tradelab.user.model.FeedType
import java.time.Instant
import java.util.UUID

data class UserSettingsChangedEvent(
    val userId: UUID,
    val feedType: FeedType,
    val updatedAt: Instant
)
