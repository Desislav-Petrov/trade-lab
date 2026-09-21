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
import org.springframework.stereotype.Service
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

@Service
class AgentService(
    private val agentRunner: Runner,
    private val agentSessionService: InMemorySessionService,
    private val agentRunConfig: RunConfig
) {
    private val sessionLocks = ConcurrentHashMap<String, SessionLock>()

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
            releaseSessionLock(lockKey, sessionLock)
        }
    }

    private fun eventToChunk(event: Event): Flowable<String> {
        val chunk = event.stringifyContent()
        return if (chunk.isBlank()) Flowable.empty() else Flowable.just(chunk)
    }

    private fun acquireSessionLock(lockKey: String): SessionLock {
        val sessionLock = sessionLocks.compute(lockKey) { _, existing ->
            (existing ?: SessionLock()).also { it.refCount.incrementAndGet() }
        }!!
        sessionLock.lock.lock()
        return sessionLock
    }

    private fun releaseSessionLock(lockKey: String, sessionLock: SessionLock) {
        sessionLock.lock.unlock()
        sessionLocks.computeIfPresent(lockKey) { _, existing ->
            if (existing !== sessionLock) {
                existing
            } else if (existing.refCount.decrementAndGet() == 0) {
                null
            } else {
                existing
            }
        }
    }

    private class SessionLock(
        val lock: ReentrantLock = ReentrantLock(),
        val refCount: AtomicInteger = AtomicInteger(0)
    )
}
