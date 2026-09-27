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
import java.util.concurrent.atomic.AtomicBoolean
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

            // Per-subscription flag: true once we have streamed a partial (delta)
            // text chunk for the current model turn. It lets us suppress the final
            // aggregated event, which repeats the whole turn's text verbatim.
            val streamedPartial = AtomicBoolean(false)

            agentRunner.runAsync(
                session.sessionKey(),
                Content.fromParts(Part.fromText(message)),
                agentRunConfig
            )
                // concatMap (not flatMap) keeps token order and makes the dedup
                // state above deterministic by processing events sequentially.
                .concatMap { event -> eventToChunk(event, streamedPartial) }
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

    /**
     * Turns one ADK [Event] into at most one user-facing text chunk.
     *
     * Two things must never reach the client:
     *  - function-call / function-response parts (agent delegation and tool I/O,
     *    e.g. the raw `getHoldings` payload) — we surface only natural-language
     *    text parts and drop everything else;
     *  - the final aggregated event of a streamed turn, which repeats the whole
     *    turn's text. In [RunConfig.StreamingMode.SSE] the model emits partial
     *    (delta) events followed by a non-partial aggregate; forwarding both
     *    doubles the reply, so we suppress the aggregate once deltas have been
     *    streamed for the current turn.
     */
    private fun eventToChunk(event: Event, streamedPartial: AtomicBoolean): Flowable<String> {
        val text = event.textContent()
        if (text.isBlank()) {
            return Flowable.empty()
        }

        if (event.partial().orElse(false)) {
            streamedPartial.set(true)
            return Flowable.just(text)
        }

        // Non-partial (complete) event. If it follows partial deltas it is the
        // aggregate of those deltas and must be dropped; otherwise it is a
        // single-shot message and must be emitted. Reset the flag so the next
        // turn is evaluated independently.
        return if (streamedPartial.getAndSet(false)) Flowable.empty() else Flowable.just(text)
    }

    private fun Event.textContent(): String =
        content()
            .flatMap { it.parts() }
            .map { parts ->
                parts.mapNotNull { part -> part.text().orElse(null) }.joinToString(separator = "")
            }
            .orElse("")

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
