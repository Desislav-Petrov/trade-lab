package org.dpp.tradelab.agent.controller

import java.util.UUID

data class TradeExecutedEventPayload(
    val accountId: UUID
)
