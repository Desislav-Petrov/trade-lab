package org.dpp.tradelab.agent.service

import java.util.UUID

sealed interface AgentStreamEvent {
    data class TextChunk(val text: String) : AgentStreamEvent
    data class TradeExecuted(val accountId: UUID) : AgentStreamEvent
}
