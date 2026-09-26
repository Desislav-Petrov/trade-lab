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
import org.dpp.tradelab.config.AGENT_APP_NAME
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Collections
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import java.util.WeakHashMap

@Service
class AgentService(
    private val agentRunner: Runner,
    private val agentSessionService: InMemorySessionService,
    private val agentRunConfig: RunConfig,
    @Value("\${app.agent.timeout-seconds:30}")
    private val timeoutSeconds: Long
) {
    companion object {
        private const val AGENT_UNAVAILABLE_MESSAGE = "The AI assistant is temporarily unavailable."
    }

    private val logger = LoggerFactory.getLogger(AgentService::class.java)
    private val sessionLocks = Collections.synchronizedMap(WeakHashMap<String, ReentrantLock>())

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
                // Fail fast if the model produces no chunk within the window (initial
                // hang or a stall mid-stream) instead of leaving the SSE connection —
                // and the frontend — blocked forever.
                .timeout(timeoutSeconds, TimeUnit.SECONDS)
                .onErrorResumeNext { error: Throwable ->
                    logAgentFailure(userId, conversationId, error)
                    if (error is AgentUnavailableException) {
                        Flowable.error(error)
                    } else {
                        Flowable.error(AgentUnavailableException(AGENT_UNAVAILABLE_MESSAGE, error))
                    }
                }
        } catch (ex: AgentUnavailableException) {
            logAgentFailure(userId, conversationId, ex)
            Flowable.error(ex)
        } catch (ex: Exception) {
            logAgentFailure(userId, conversationId, ex)
            Flowable.error(AgentUnavailableException(AGENT_UNAVAILABLE_MESSAGE, ex))
        }

    private fun logAgentFailure(userId: UUID, conversationId: UUID, error: Throwable) {
        if (error is TimeoutException) {
            logger.error(
                "Agent query timed out after {}s with no model response for user={} conversation={}",
                timeoutSeconds,
                userId,
                conversationId,
                error
            )
        } else {
            logger.error(
                "Agent query failed for user={} conversation={}: {}",
                userId,
                conversationId,
                error.message,
                error
            )
        }
    }

    private fun loadOrCreateSession(userId: UUID, accountId: UUID, conversationId: UUID): Session {
        val userIdValue = userId.toString()
        val conversationIdValue = conversationId.toString()
        val lockKey = "$userIdValue:$conversationIdValue"
        val sessionLock = acquireSessionLock(lockKey)

        try {
            val existing = agentSessionService.getSession(
                AGENT_APP_NAME,
                userIdValue,
                conversationIdValue,
                Optional.empty()
            )
                .map { Optional.of(it) }
                .defaultIfEmpty(Optional.empty())
                .blockingGet()

            if (existing.isPresent) {
                return existing.get()
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
        } finally {
            releaseSessionLock(sessionLock)
        }
    }

    private fun eventToChunk(event: Event): Flowable<String> {
        val chunk = event.stringifyContent()
        return if (chunk.isBlank()) Flowable.empty() else Flowable.just(chunk)
    }

    private fun acquireSessionLock(lockKey: String): SessionLock {
        val sessionLock = synchronized(sessionLocks) {
            sessionLocks.getOrPut(lockKey) { ReentrantLock() }
        }
        sessionLock.lock()
        return SessionLock(sessionLock)
    }

    private fun releaseSessionLock(sessionLock: SessionLock) {
        sessionLock.lock.unlock()
    }

    private class SessionLock(
        val lock: ReentrantLock
    )
}
