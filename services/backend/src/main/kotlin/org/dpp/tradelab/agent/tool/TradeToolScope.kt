package org.dpp.tradelab.agent.tool

import com.google.adk.tools.ToolContext
import java.util.UUID

internal data class TradeToolScope(
    val conversationId: UUID,
    val userId: UUID,
    val accountId: UUID
) {
    companion object {
        fun from(toolContext: ToolContext): TradeToolScope {
            val authenticatedUserId = requireAuthenticatedUserId(toolContext)
            val stateUserId = requireUuid(toolContext, "userId")
            check(stateUserId == authenticatedUserId) {
                "userId in agent session state does not match authenticated user"
            }
            return TradeToolScope(
                conversationId = requireConversationId(toolContext),
                userId = authenticatedUserId,
                accountId = requireUuid(toolContext, "accountId")
            )
        }

        private fun requireConversationId(toolContext: ToolContext): UUID =
            try {
                val sessionId = toolContext.invocationContext().session()?.sessionKey()?.id()
                    ?: throw IllegalStateException("Missing conversationId in agent session")
                UUID.fromString(sessionId)
            } catch (ex: IllegalArgumentException) {
                throw IllegalStateException("Invalid conversationId in agent session", ex)
            }

        private fun requireUuid(toolContext: ToolContext, key: String): UUID {
            val value = toolContext.state()[key]?.toString()
                ?: error("Missing $key in agent session state")
            return try {
                UUID.fromString(value)
            } catch (ex: IllegalArgumentException) {
                throw IllegalStateException("Invalid $key in agent session state", ex)
            }
        }

        private fun requireAuthenticatedUserId(toolContext: ToolContext): UUID =
            try {
                val value = toolContext.userId()?.takeIf(String::isNotBlank)
                    ?: toolContext.invocationContext().session()?.userId()?.takeIf(String::isNotBlank)
                    ?: throw IllegalStateException("Missing authenticated userId in agent session")
                UUID.fromString(value)
            } catch (ex: IllegalArgumentException) {
                throw IllegalStateException("Invalid authenticated userId in agent session", ex)
            }
    }
}
