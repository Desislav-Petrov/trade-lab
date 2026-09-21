package org.dpp.tradelab.agent.service

import com.google.adk.agents.RunConfig
import com.google.adk.events.Event
import com.google.adk.runner.Runner
import com.google.adk.sessions.InMemorySessionService
import com.google.adk.sessions.Session
import com.google.genai.types.Content
import com.google.genai.types.Part
import io.reactivex.rxjava3.core.Flowable
import org.dpp.tradelab.agent.exception.AgentUnavailableException
import org.springframework.stereotype.Service
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class AgentService(
    private val agentRunner: Runner,
    private val agentSessionService: InMemorySessionService,
    private val agentRunConfig: RunConfig
) {

    fun query(
        userId: UUID,
        accountId: UUID,
        conversationId: UUID,
        message: String
    ): Flowable<String> =
        try {
            val session = loadOrCreateSession(userId, accountId, conversationId)

            agentRunner.runAsync(
                session.sessionKey(),
                Content.fromParts(Part.fromText(message)),
                agentRunConfig
            )
                .flatMap { event -> eventToChunk(event) }
                .onErrorResumeNext { error: Throwable ->
                    Flowable.error(AgentUnavailableException("The AI assistant is temporarily unavailable.", error))
                }
        } catch (ex: AgentUnavailableException) {
            Flowable.error(ex)
        } catch (ex: Exception) {
            Flowable.error(AgentUnavailableException("The AI assistant is temporarily unavailable.", ex))
        }

    private fun loadOrCreateSession(userId: UUID, accountId: UUID, conversationId: UUID): Session {
        val userIdValue = userId.toString()
        val conversationIdValue = conversationId.toString()

        val existing = agentSessionService.getSession(
            AGENT_APP_NAME,
            userIdValue,
            conversationIdValue,
            Optional.empty()
        ).blockingGet()

        if (existing != null) {
            return existing
        }

        val state = ConcurrentHashMap<String, Any>()
        state["userId"] = userIdValue
        state["accountId"] = accountId.toString()

        return agentSessionService.createSession(
            AGENT_APP_NAME,
            userIdValue,
            state,
            conversationIdValue
        ).blockingGet()
    }

    private fun eventToChunk(event: Event): Flowable<String> {
        val chunk = event.stringifyContent()
        return if (chunk.isBlank()) Flowable.empty() else Flowable.just(chunk)
    }
}
