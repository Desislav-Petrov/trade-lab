package org.dpp.tradelab.agent.service

import com.google.adk.events.Event
import com.google.adk.sessions.BaseSessionService
import com.google.adk.sessions.GetSessionConfig
import com.google.adk.sessions.ListEventsResponse
import com.google.adk.sessions.ListSessionsResponse
import com.google.adk.sessions.Session
import com.google.adk.sessions.SessionKey
import com.google.adk.sessions.State
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import org.dpp.tradelab.agent.exception.SessionPersistenceException
import org.dpp.tradelab.agent.model.AgentEvent
import org.dpp.tradelab.agent.model.AgentSession
import org.dpp.tradelab.agent.model.AgentUserState
import org.dpp.tradelab.agent.repository.AgentEventRepository
import org.dpp.tradelab.agent.repository.AgentSessionRepository
import org.dpp.tradelab.agent.repository.AgentUserStateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Service
class PersistentSessionService(
    private val agentSessionRepository: AgentSessionRepository,
    private val agentEventRepository: AgentEventRepository,
    private val agentUserStateRepository: AgentUserStateRepository,
    private val serializer: AdkJsonSerializer,
    private val userStateProvisioner: AgentUserStateProvisioner
) : BaseSessionService {
    private val logger = LoggerFactory.getLogger(PersistentSessionService::class.java)

    private companion object {
        const val LAST_CONVERSATION_KEY = "user:last_conversation"
        const val LAST_CONVERSATION_SOURCE_TIME_KEY = "user:last_conversation_source_time"
    }

    @Transactional
    override fun createSession(
        appName: String,
        userId: String,
        state: ConcurrentMap<String, Any>?,
        sessionId: String?
    ): Single<Session> =
        try {
            val id = sessionId?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
            if (agentSessionRepository.findByAppNameAndUserIdAndSessionId(appName, userId, id) != null) {
                throw SessionPersistenceException("An agent session with this identifier already exists.")
            }

            val initialState = serializer.sanitizeState(state ?: emptyMap<String, Any>())
            val sessionState = initialState.filterKeys { !it.startsWith(State.USER_PREFIX) }
            val userState = initialState.filterKeys { it.startsWith(State.USER_PREFIX) }
            userStateProvisioner.ensureExists(appName, userId)
            val userStateEntity = agentUserStateRepository.findByAppNameAndUserIdForUpdate(appName, userId)
                ?: throw SessionPersistenceException("Could not provision agent user state.")
            if (userState.isNotEmpty()) {
                val values = serializer.deserializeState(userStateEntity.stateJson)
                values.putAll(userState)
                userStateEntity.stateJson = serializer.serializeState(values)
            }
            if (userState.isNotEmpty()) {
                agentUserStateRepository.saveAndFlush(userStateEntity)
            }

            val session = agentSessionRepository.saveAndFlush(
                AgentSession(
                    id = UUID.randomUUID(),
                    appName = appName,
                    userId = userId,
                    sessionId = id,
                    stateJson = serializer.serializeState(sessionState)
                )
            )
            Single.just(buildSession(session, loadUserState(appName, userId)))
        } catch (ex: SessionPersistenceException) {
            throw ex
        } catch (ex: Exception) {
            throw persistenceFailure("Could not create agent session.", ex)
        }

    @Transactional(readOnly = true)
    override fun getSession(
        appName: String,
        userId: String,
        sessionId: String,
        config: Optional<GetSessionConfig>
    ): Maybe<Session> =
        try {
            val session = agentSessionRepository.findByAppNameAndUserIdAndSessionId(appName, userId, sessionId)
                ?: return Maybe.empty()
            val options = config.orElse(null)
            val events = loadEvents(appName, userId, sessionId)
                .map { serializer.deserializeEvent(it.eventJson) }
                .filter { event ->
                    options?.afterTimestamp()?.orElse(null)?.let { Instant.ofEpochMilli(event.timestamp()).isAfter(it) } != false
                }
                .let { filtered ->
                    options?.numRecentEvents()?.orElse(null)?.let(filtered::takeLast) ?: filtered
                }
            Maybe.just(buildSession(session, loadUserState(appName, userId), events))
        } catch (ex: SessionPersistenceException) {
            throw ex
        } catch (ex: Exception) {
            throw persistenceFailure("Could not retrieve agent session.", ex)
        }

    @Transactional(readOnly = true)
    override fun listSessions(appName: String, userId: String): Single<ListSessionsResponse> =
        try {
            val userState = loadUserState(appName, userId)
            val sessions = agentSessionRepository.findAllByAppNameAndUserId(appName, userId)
                .map { buildSession(it, userState) }
            Single.just(ListSessionsResponse.builder().sessions(sessions).build())
        } catch (ex: SessionPersistenceException) {
            throw ex
        } catch (ex: Exception) {
            throw persistenceFailure("Could not list agent sessions.", ex)
        }

    @Transactional
    override fun deleteSession(appName: String, userId: String, sessionId: String): Completable =
        try {
            agentEventRepository.deleteAllByAppNameAndUserIdAndSessionId(appName, userId, sessionId)
            agentSessionRepository.deleteByAppNameAndUserIdAndSessionId(appName, userId, sessionId)
            Completable.complete()
        } catch (ex: Exception) {
            throw persistenceFailure("Could not delete agent session.", ex)
        }

    @Transactional(readOnly = true)
    override fun listEvents(appName: String, userId: String, sessionId: String): Single<ListEventsResponse> =
        try {
            val events = loadEvents(appName, userId, sessionId).map { serializer.deserializeEvent(it.eventJson) }
            Single.just(ListEventsResponse.builder().events(events).build())
        } catch (ex: SessionPersistenceException) {
            throw ex
        } catch (ex: Exception) {
            throw persistenceFailure("Could not list agent events.", ex)
        }

    @Transactional
    override fun appendEvent(session: Session, event: Event): Single<Event> {
        if (event.partial().orElse(false)) return Single.just(event)

        return try {
            val key = session.sessionKey()
            val sessionEntity = agentSessionRepository.findByAppNameAndUserIdAndSessionIdForUpdate(
                key.appName(),
                key.userId(),
                key.id()
            ) ?: throw SessionPersistenceException("Agent session does not exist.")
            val userStateEntity = lockOrCreateUserState(key.appName(), key.userId())
            val sessionState = serializer.deserializeState(sessionEntity.stateJson)
            val userState = serializer.deserializeState(userStateEntity.stateJson)

            val delta = event.actions()?.stateDelta().orEmpty()
            delta.forEach { (stateKey, value) ->
                when {
                    stateKey.startsWith(State.TEMP_PREFIX) -> Unit
                    stateKey.startsWith(State.USER_PREFIX) -> applyStateDelta(userState, stateKey, value)
                    else -> applyStateDelta(sessionState, stateKey, value)
                }
            }

            updateAdkSessionState(session.state(), sessionState, userState)
            sessionEntity.stateJson = serializer.serializeState(sessionState)
            sessionEntity.updatedAt = Instant.now()
            userStateEntity.stateJson = serializer.serializeState(userState)

            val nextSequence = (agentEventRepository.findMaxSequence(key.appName(), key.userId(), key.id()) ?: 0L) + 1L
            agentEventRepository.saveAndFlush(
                AgentEvent(
                    id = UUID.randomUUID(),
                    appName = key.appName(),
                    userId = key.userId(),
                    sessionId = key.id(),
                    eventJson = serializer.serializeEvent(event),
                    eventTimestamp = Instant.ofEpochMilli(event.timestamp()),
                    sequence = nextSequence
                )
            )
            agentSessionRepository.saveAndFlush(sessionEntity)
            agentUserStateRepository.saveAndFlush(userStateEntity)
            session.events().add(event)
            session.lastUpdateTime(sessionEntity.updatedAt)
            Single.just(event)
        } catch (ex: SessionPersistenceException) {
            throw ex
        } catch (ex: Exception) {
            throw persistenceFailure("Could not append ADK event.", ex)
        }
    }

    @Transactional(readOnly = true)
    fun getUserScopedValue(appName: String, userId: String, key: String): Any? =
        try {
            loadUserState(appName, userId)[key]
        } catch (ex: Exception) {
            throw persistenceFailure("Could not retrieve agent user state.", ex)
        }

    @Transactional
    fun updateUserScopedValue(appName: String, userId: String, key: String, value: Any) {
        if (!key.startsWith(State.USER_PREFIX)) {
            throw SessionPersistenceException("Agent user state keys must use the ADK user scope prefix.")
        }
        try {
            val entity = lockOrCreateUserState(appName, userId)
            val state = serializer.deserializeState(entity.stateJson)
            state[key] = value
            entity.stateJson = serializer.serializeState(state)
            agentUserStateRepository.saveAndFlush(entity)
        } catch (ex: SessionPersistenceException) {
            throw ex
        } catch (ex: Exception) {
            throw persistenceFailure("Could not update agent user state.", ex)
        }
    }

    @Transactional
    fun updateConversationSummaryIfNewer(
        appName: String,
        userId: String,
        summary: String,
        sourceUpdatedAt: Instant
    ): Boolean =
        try {
            val entity = lockOrCreateUserState(appName, userId)
            val state = serializer.deserializeState(entity.stateJson)
            val currentSourceTime = (state[LAST_CONVERSATION_SOURCE_TIME_KEY] as? String)
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (currentSourceTime != null && currentSourceTime.isAfter(sourceUpdatedAt)) {
                false
            } else {
                state[LAST_CONVERSATION_KEY] = summary
                state[LAST_CONVERSATION_SOURCE_TIME_KEY] = sourceUpdatedAt.toString()
                entity.stateJson = serializer.serializeState(state)
                agentUserStateRepository.saveAndFlush(entity)
                true
            }
        } catch (ex: SessionPersistenceException) {
            throw ex
        } catch (ex: Exception) {
            throw persistenceFailure("Could not update agent conversation summary.", ex)
        }

    private fun loadEvents(appName: String, userId: String, sessionId: String): List<AgentEvent> =
        agentEventRepository.findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc(appName, userId, sessionId)

    private fun loadUserState(appName: String, userId: String): ConcurrentMap<String, Any> =
        agentUserStateRepository.findByAppNameAndUserId(appName, userId)
            ?.let { serializer.deserializeState(it.stateJson) }
            ?: ConcurrentHashMap()

    private fun lockOrCreateUserState(appName: String, userId: String): AgentUserState {
        userStateProvisioner.ensureExists(appName, userId)
        return agentUserStateRepository.findByAppNameAndUserIdForUpdate(appName, userId)
            ?: throw SessionPersistenceException("Could not provision agent user state.")
    }

    private fun buildSession(
        entity: AgentSession,
        userState: Map<String, Any>,
        events: List<Event> = loadEvents(entity.appName, entity.userId, entity.sessionId)
            .map { serializer.deserializeEvent(it.eventJson) }
    ): Session {
        val state = serializer.deserializeState(entity.stateJson)
        state.putAll(userState)
        return Session.builder(SessionKey(entity.appName, entity.userId, entity.sessionId))
            .state(state)
            .events(events.toMutableList())
            .lastUpdateTime(entity.updatedAt ?: entity.createdAt ?: Instant.now())
            .build()
    }

    private fun applyStateDelta(state: MutableMap<String, Any>, key: String, value: Any?) {
        if (value === State.REMOVED) {
            state.remove(key)
        } else if (value != null) {
            serializer.sanitizeState(mapOf(key to value))[key]?.let { state[key] = it }
        }
    }

    private fun updateAdkSessionState(
        adkState: MutableMap<String, Any>,
        sessionState: Map<String, Any>,
        userState: Map<String, Any>
    ) {
        adkState.clear()
        adkState.putAll(sessionState)
        adkState.putAll(userState)
    }

    private fun persistenceFailure(message: String, ex: Exception): SessionPersistenceException {
        logger.error(message, ex)
        return SessionPersistenceException(message, ex)
    }
}
