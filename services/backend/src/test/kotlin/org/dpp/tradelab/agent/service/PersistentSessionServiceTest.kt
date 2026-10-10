package org.dpp.tradelab.agent.service

import com.google.adk.events.Event
import com.google.adk.sessions.GetSessionConfig
import com.google.adk.sessions.Session
import com.google.adk.sessions.SessionKey
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.agent.exception.SessionPersistenceException
import org.dpp.tradelab.agent.model.AgentEvent
import org.dpp.tradelab.agent.model.AgentSession
import org.dpp.tradelab.agent.model.AgentUserState
import org.dpp.tradelab.agent.repository.AgentEventRepository
import org.dpp.tradelab.agent.repository.AgentSessionRepository
import org.dpp.tradelab.agent.repository.AgentUserStateRepository
import org.dpp.tradelab.config.AGENT_APP_NAME
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PersistentSessionServiceTest : FunSpec() {
    private val sessionRepository = mock<AgentSessionRepository>()
    private val eventRepository = mock<AgentEventRepository>()
    private val userStateRepository = mock<AgentUserStateRepository>()
    private val serializer = AdkJsonSerializer()
    private val service = PersistentSessionService(sessionRepository, eventRepository, userStateRepository, serializer)
    private val userId = "user-1"
    private val sessionId = "session-1"
    private val sessionKey = SessionKey(AGENT_APP_NAME, userId, sessionId)
    private val sessionEntity = AgentSession(
        UUID.randomUUID(),
        AGENT_APP_NAME,
        userId,
        sessionId,
        """{"accountId":"account-1"}"""
    )
    private val userStateEntity = AgentUserState(UUID.randomUUID(), AGENT_APP_NAME, userId, "{}")

    init {
        beforeEach {
            reset(sessionRepository, eventRepository, userStateRepository)
            sessionEntity.stateJson = """{"accountId":"account-1"}"""
            userStateEntity.stateJson = "{}"
        }

        test("createSession_persistsSessionAndUserState") {
            whenever(sessionRepository.findByAppNameAndUserIdAndSessionId(AGENT_APP_NAME, userId, sessionId))
                .thenReturn(null)
            whenever(userStateRepository.findByAppNameAndUserIdForUpdate(AGENT_APP_NAME, userId))
                .thenReturn(null)
            whenever(userStateRepository.saveAndFlush(any<AgentUserState>()))
                .thenAnswer { it.getArgument<AgentUserState>(0) }
            whenever(userStateRepository.findByAppNameAndUserId(AGENT_APP_NAME, userId))
                .thenReturn(userStateEntity)
            whenever(sessionRepository.saveAndFlush(any<AgentSession>()))
                .thenAnswer { it.getArgument<AgentSession>(0) }
            whenever(eventRepository.findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc(AGENT_APP_NAME, userId, sessionId))
                .thenReturn(emptyList())

            val result = service.createSession(
                AGENT_APP_NAME,
                userId,
                ConcurrentHashMap(mapOf("accountId" to "account-1", "user:hello" to "world")),
                sessionId
            ).blockingGet()

            result.id() shouldBe sessionId
            result.state()["accountId"] shouldBe "account-1"
            verify(sessionRepository).saveAndFlush(any<AgentSession>())
            verify(userStateRepository).saveAndFlush(any<AgentUserState>())
        }

        test("getSession_rehydratesStateAndHonoursRecentEvents") {
            val event = event("latest", 2)
            whenever(sessionRepository.findByAppNameAndUserIdAndSessionId(AGENT_APP_NAME, userId, sessionId))
                .thenReturn(sessionEntity)
            whenever(userStateRepository.findByAppNameAndUserId(AGENT_APP_NAME, userId))
                .thenReturn(userStateEntity)
            whenever(eventRepository.findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc(AGENT_APP_NAME, userId, sessionId))
                .thenReturn(listOf(eventRow(event, 2)))

            val result = service.getSession(
                AGENT_APP_NAME,
                userId,
                sessionId,
                Optional.of(GetSessionConfig.builder().numRecentEvents(1).build())
            ).blockingGet()!!

            result.state()["accountId"] shouldBe "account-1"
            result.events().map { it.id() } shouldContainExactly listOf(event.id())
        }

        test("getSession_sessionMissing_returnsEmpty") {
            whenever(sessionRepository.findByAppNameAndUserIdAndSessionId(AGENT_APP_NAME, userId, sessionId))
                .thenReturn(null)

            service.getSession(AGENT_APP_NAME, userId, sessionId, Optional.empty()).isEmpty.blockingGet() shouldBe true
        }

        test("listSessions_returnsSessionsWithMergedState") {
            whenever(sessionRepository.findAllByAppNameAndUserId(AGENT_APP_NAME, userId)).thenReturn(listOf(sessionEntity))
            whenever(userStateRepository.findByAppNameAndUserId(AGENT_APP_NAME, userId)).thenReturn(userStateEntity)
            whenever(eventRepository.findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc(AGENT_APP_NAME, userId, sessionId))
                .thenReturn(emptyList())

            service.listSessions(AGENT_APP_NAME, userId).blockingGet().sessions().single().id() shouldBe sessionId
        }

        test("deleteSession_removesEventsAndSession") {
            whenever(eventRepository.deleteAllByAppNameAndUserIdAndSessionId(AGENT_APP_NAME, userId, sessionId)).thenReturn(2)
            whenever(sessionRepository.deleteByAppNameAndUserIdAndSessionId(AGENT_APP_NAME, userId, sessionId)).thenReturn(1)

            service.deleteSession(AGENT_APP_NAME, userId, sessionId).blockingAwait()

            verify(eventRepository).deleteAllByAppNameAndUserIdAndSessionId(AGENT_APP_NAME, userId, sessionId)
            verify(sessionRepository).deleteByAppNameAndUserIdAndSessionId(AGENT_APP_NAME, userId, sessionId)
        }

        test("listEvents_returnsEventsInSequenceOrder") {
            val first = event("first", 1)
            val second = event("second", 2)
            whenever(eventRepository.findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc(AGENT_APP_NAME, userId, sessionId))
                .thenReturn(listOf(eventRow(first, 1), eventRow(second, 2)))

            service.listEvents(AGENT_APP_NAME, userId, sessionId).blockingGet().events().map { it.id() } shouldContainExactly
                listOf(first.id(), second.id())
        }

        test("appendEvent_locksSessionAndPersistsEventAndUpdatedState") {
            whenever(sessionRepository.findByAppNameAndUserIdAndSessionIdForUpdate(AGENT_APP_NAME, userId, sessionId))
                .thenReturn(sessionEntity)
            whenever(userStateRepository.findByAppNameAndUserIdForUpdate(AGENT_APP_NAME, userId))
                .thenReturn(userStateEntity)
            whenever(eventRepository.findMaxSequence(AGENT_APP_NAME, userId, sessionId)).thenReturn(3L)
            whenever(eventRepository.saveAndFlush(any<AgentEvent>())).thenAnswer { it.getArgument<AgentEvent>(0) }
            whenever(sessionRepository.saveAndFlush(any<AgentSession>())).thenAnswer { it.getArgument<AgentSession>(0) }
            whenever(userStateRepository.saveAndFlush(any<AgentUserState>())).thenAnswer { it.getArgument<AgentUserState>(0) }
            val adkSession = Session.builder(sessionKey)
                .state(mapOf("accountId" to "account-1"))
                .events(mutableListOf())
                .build()

            val result = service.appendEvent(adkSession, event("appended", 4)).blockingGet()

            result.author() shouldBe "assistant"
            adkSession.events().size shouldBe 1
            verify(sessionRepository).findByAppNameAndUserIdAndSessionIdForUpdate(AGENT_APP_NAME, userId, sessionId)
            verify(eventRepository).saveAndFlush(any<AgentEvent>())
            verify(sessionRepository).saveAndFlush(any<AgentSession>())
            verify(userStateRepository).saveAndFlush(any<AgentUserState>())
        }

        test("appendEvent_repositoryFailure_throwsTypedException") {
            whenever(sessionRepository.findByAppNameAndUserIdAndSessionIdForUpdate(AGENT_APP_NAME, userId, sessionId))
                .thenThrow(DataAccessResourceFailureException("database unavailable"))
            val adkSession = Session.builder(sessionKey).events(mutableListOf()).build()

            shouldThrow<SessionPersistenceException> {
                service.appendEvent(adkSession, event("appended", 4))
            }
        }

        test("userStateReadAndWrite_shareUserScopedState") {
            whenever(userStateRepository.findByAppNameAndUserId(AGENT_APP_NAME, userId)).thenReturn(userStateEntity)
            whenever(userStateRepository.findByAppNameAndUserIdForUpdate(AGENT_APP_NAME, userId)).thenReturn(userStateEntity)
            whenever(userStateRepository.saveAndFlush(any<AgentUserState>())).thenAnswer { it.getArgument<AgentUserState>(0) }

            service.updateUserScopedValue(AGENT_APP_NAME, userId, "user:last_conversation", "summary")

            userStateEntity.stateJson = """{"user:last_conversation":"summary"}"""
            service.getUserScopedValue(AGENT_APP_NAME, userId, "user:last_conversation") shouldBe "summary"
        }

        test("getSession_databaseFailure_throwsTypedException") {
            whenever(sessionRepository.findByAppNameAndUserIdAndSessionId(AGENT_APP_NAME, userId, sessionId))
                .thenThrow(DataAccessResourceFailureException("database unavailable"))

            shouldThrow<SessionPersistenceException> {
                service.getSession(AGENT_APP_NAME, userId, sessionId, Optional.empty())
            }
        }
    }

    private fun event(text: String, timestampSeconds: Long): Event =
        Event.builder()
            .id("event-$timestampSeconds")
            .author("assistant")
            .content(com.google.genai.types.Content.fromParts(com.google.genai.types.Part.fromText(text)))
            .timestamp(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(timestampSeconds).toEpochMilli())
            .build()

    private fun eventRow(event: Event, sequence: Long): AgentEvent =
        AgentEvent(
            UUID.randomUUID(),
            AGENT_APP_NAME,
            userId,
            sessionId,
            serializer.serializeEvent(event),
            Instant.ofEpochMilli(event.timestamp()),
            sequence
        )
}
