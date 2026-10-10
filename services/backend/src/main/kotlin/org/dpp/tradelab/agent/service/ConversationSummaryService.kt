package org.dpp.tradelab.agent.service

import com.google.adk.events.Event
import com.google.adk.models.Gemini
import com.google.adk.models.LlmRequest
import com.google.adk.sessions.Session
import com.google.genai.types.Content
import com.google.genai.types.Part
import org.dpp.tradelab.config.AGENT_APP_NAME
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Optional
import java.util.UUID
import java.time.Instant

@Service
class ConversationSummaryService(
    private val agentModel: Gemini,
    private val persistentSessionService: PersistentSessionService
) {
    private val logger = LoggerFactory.getLogger(ConversationSummaryService::class.java)

    fun regenerate(userId: UUID, conversationId: UUID) {
        try {
            val session = persistentSessionService.getSession(
                AGENT_APP_NAME,
                userId.toString(),
                conversationId.toString(),
                Optional.empty()
            ).blockingGet() ?: return

            val transcript = session.events().mapNotNull(::formatTurn)
            if (transcript.isEmpty()) return

            val prompt = """
                Produce a concise natural-language summary of the conversation below for the user's next visit.
                Preserve the user's goals, important conclusions, and unresolved follow-ups. Do not invent facts,
                expose tool payloads, or include a greeting. Use two or three short sentences.

                Conversation:
                ${transcript.joinToString("\n")}
            """.trimIndent()
            val request = LlmRequest.builder()
                .model(agentModel.model())
                .contents(listOf(Content.fromParts(Part.fromText(prompt))))
                .build()
            val summary = agentModel.generateContent(request, false)
                .blockingIterable()
                .mapNotNull { response ->
                    response.content().orElse(null)?.parts()?.orElse(emptyList())
                        ?.mapNotNull { part ->
                            if (part.functionCall().isPresent || part.functionResponse().isPresent) {
                                null
                            } else {
                                part.text().orElse(null)
                            }
                        }?.joinToString("")
                }
                .joinToString("")
                .trim()

            if (summary.isBlank()) throw IllegalStateException("Summary model returned an empty response.")
            persistentSessionService.updateConversationSummaryIfNewer(
                AGENT_APP_NAME,
                userId.toString(),
                summary,
                session.lastUpdateTime() ?: Instant.now()
            )
        } catch (ex: Exception) {
            logger.warn("Could not regenerate assistant summary for user={} conversation={}", userId, conversationId, ex)
        }
    }

    fun getSummary(userId: UUID): String? =
        try {
            persistentSessionService.getUserScopedValue(
                AGENT_APP_NAME,
                userId.toString(),
                "user:last_conversation"
            ) as? String
        } catch (ex: Exception) {
            logger.warn("Could not retrieve assistant summary for user={}", userId, ex)
            null
        }

    private fun formatTurn(event: Event): String? {
        val text = event.content().flatMap { it.parts() }.orElse(emptyList())
            .mapNotNull { part ->
                if (part.functionCall().isPresent || part.functionResponse().isPresent) null
                else part.text().orElse(null)
            }
            .joinToString("")
            .trim()
        if (text.isBlank()) return null
        val role = if (event.author() == "user") "User" else "Assistant"
        return "$role: $text"
    }
}
