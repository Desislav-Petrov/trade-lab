package org.dpp.tradelab.agent.service

import com.google.adk.events.Event
import com.google.adk.events.EventActions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.agent.exception.SessionPersistenceException
import org.dpp.tradelab.agent.model.AgentUserState
import org.dpp.tradelab.agent.repository.AgentEventRepository
import org.dpp.tradelab.agent.repository.AgentSessionRepository
import org.dpp.tradelab.agent.repository.AgentUserStateRepository
import org.dpp.tradelab.config.AGENT_APP_NAME
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PersistentSessionServicePostgresTest @Autowired constructor(
    private val service: PersistentSessionService,
    private val sessions: AgentSessionRepository,
    private val events: AgentEventRepository,
    private val states: AgentUserStateRepository,
    private val serializer: AdkJsonSerializer
) {
    @MockitoSpyBean
    private lateinit var userStateRepositorySpy: AgentUserStateRepository

    @BeforeEach
    fun clearDatabase() {
        events.deleteAll()
        sessions.deleteAll()
        states.deleteAll()
    }

    @Test
    fun createAppendRestartReadListAndDelete_areDurableAndOrdered() {
        val userId = UUID.randomUUID().toString()
        val conversationId = UUID.randomUUID().toString()
        val state = ConcurrentHashMap<String, Any>().apply {
            put("accountId", "account-1")
            put("user:preference", "shared")
        }
        val created = service.createSession(AGENT_APP_NAME, userId, state, conversationId).blockingGet()
        service.appendEvent(created, event("first", 1)).blockingGet()
        service.appendEvent(created, event("second", 2)).blockingGet()
        service.updateUserScopedValue(AGENT_APP_NAME, userId, "user:last_conversation", "Previous discussion")

        val restartedService = PersistentSessionService(sessions, events, states, serializer, mock())
        val restored = restartedService.getSession(AGENT_APP_NAME, userId, conversationId, Optional.empty()).blockingGet()!!

        restored.state()["accountId"] shouldBe "account-1"
        restored.state()["user:preference"] shouldBe "shared"
        restored.state()["user:last_conversation"] shouldBe "Previous discussion"
        restored.events().map { text(it) } shouldContainExactly listOf("first", "second")
        events.findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc(AGENT_APP_NAME, userId, conversationId)
            .map { it.sequence } shouldContainExactly listOf(1L, 2L)
        restartedService.listSessions(AGENT_APP_NAME, userId).blockingGet().sessionIds() shouldContainExactly listOf(conversationId)

        service.deleteSession(AGENT_APP_NAME, userId, conversationId).blockingAwait()

        sessions.findByAppNameAndUserIdAndSessionId(AGENT_APP_NAME, userId, conversationId) shouldBe null
        events.findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc(AGENT_APP_NAME, userId, conversationId) shouldBe emptyList()
    }

    @Test
    fun concurrentAppends_sameSessionRetainAllStateUpdatesAndEvents() {
        val userId = UUID.randomUUID().toString()
        val conversationId = UUID.randomUUID().toString()
        service.createSession(AGENT_APP_NAME, userId, ConcurrentHashMap(), conversationId).blockingGet()
        val numberOfAppends = 24
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)

        try {
            val tasks = (0 until numberOfAppends).map { index ->
                executor.submit {
                    val session = service.getSession(AGENT_APP_NAME, userId, conversationId, Optional.empty()).blockingGet()!!
                    start.await()
                    service.appendEvent(
                        session,
                        event("turn-$index", index.toLong() + 1, mapOf("turn-$index" to "saved-$index"))
                    ).blockingGet()
                }
            }
            start.countDown()
            tasks.forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }

        val restored = service.getSession(AGENT_APP_NAME, userId, conversationId, Optional.empty()).blockingGet()!!
        (0 until numberOfAppends).forEach { index ->
            restored.state()["turn-$index"] shouldBe "saved-$index"
        }
        events.findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc(AGENT_APP_NAME, userId, conversationId)
            .map { it.sequence } shouldContainExactly (1L..numberOfAppends.toLong()).toList()
    }

    @Test
    fun appendEvent_writeFailureRollsBackEventAndState() {
        val userId = UUID.randomUUID().toString()
        val conversationId = UUID.randomUUID().toString()
        val session = service.createSession(AGENT_APP_NAME, userId, ConcurrentHashMap(), conversationId).blockingGet()
        clearInvocations(userStateRepositorySpy)
        doThrow(DataAccessResourceFailureException("simulated state write failure"))
            .whenever(userStateRepositorySpy)
            .saveAndFlush(any<AgentUserState>())

        shouldThrow<SessionPersistenceException> {
            service.appendEvent(
                session,
                event("failed event", 1, mapOf("user:last_conversation" to "must not persist"))
            )
        }

        events.findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc(AGENT_APP_NAME, userId, conversationId) shouldBe emptyList()
        val restored = service.getSession(AGENT_APP_NAME, userId, conversationId, Optional.empty()).blockingGet()!!
        restored.state().containsKey("user:last_conversation") shouldBe false
        verify(userStateRepositorySpy).saveAndFlush(any<AgentUserState>())
    }

    @Test
    fun userState_isSharedAcrossSessionsAndIsolatedByUser() {
        val userId = UUID.randomUUID().toString()
        val otherUserId = UUID.randomUUID().toString()
        val firstSessionId = UUID.randomUUID().toString()
        val secondSessionId = UUID.randomUUID().toString()
        val otherSessionId = UUID.randomUUID().toString()
        service.createSession(AGENT_APP_NAME, userId, ConcurrentHashMap(), firstSessionId).blockingGet()
        service.updateUserScopedValue(AGENT_APP_NAME, userId, "user:last_conversation", "shared summary")
        service.createSession(AGENT_APP_NAME, userId, ConcurrentHashMap(), secondSessionId).blockingGet()
        service.createSession(AGENT_APP_NAME, otherUserId, ConcurrentHashMap(), otherSessionId).blockingGet()

        service.getSession(AGENT_APP_NAME, userId, secondSessionId, Optional.empty())
            .blockingGet()!!.state()["user:last_conversation"] shouldBe "shared summary"
        service.getSession(AGENT_APP_NAME, otherUserId, otherSessionId, Optional.empty())
            .blockingGet()!!.state().containsKey("user:last_conversation") shouldBe false
    }

    @Test
    fun concurrentFirstSessions_sameUserCreateSingleUserStateRow() {
        val userId = UUID.randomUUID().toString()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(4)

        try {
            val tasks = (0 until 4).map { index ->
                executor.submit {
                    start.await()
                    service.createSession(
                        AGENT_APP_NAME,
                        userId,
                        ConcurrentHashMap(),
                        "first-session-$index"
                    ).blockingGet()
                }
            }
            start.countDown()
            tasks.forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }

        sessions.findAllByAppNameAndUserId(AGENT_APP_NAME, userId).size shouldBe 4
        (states.findByAppNameAndUserId(AGENT_APP_NAME, userId) != null) shouldBe true
    }

    @Test
    fun summaryUpdate_olderGenerationCannotOverwriteNewerSummary() {
        val userId = UUID.randomUUID().toString()
        val newerTimestamp = Instant.parse("2026-10-10T10:00:00Z")

        service.updateConversationSummaryIfNewer(AGENT_APP_NAME, userId, "newer summary", newerTimestamp) shouldBe true
        service.updateConversationSummaryIfNewer(
            AGENT_APP_NAME,
            userId,
            "older summary",
            newerTimestamp.minusSeconds(1)
        ) shouldBe false

        service.getUserScopedValue(AGENT_APP_NAME, userId, "user:last_conversation") shouldBe "newer summary"
    }

    private fun event(text: String, timestampSeconds: Long, stateDelta: Map<String, Any> = emptyMap()): Event =
        Event.builder()
            .id(UUID.randomUUID().toString())
            .author("assistant")
            .actions(EventActions.builder().stateDelta(stateDelta).build())
            .content(com.google.genai.types.Content.fromParts(com.google.genai.types.Part.fromText(text)))
            .timestamp(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(timestampSeconds).toEpochMilli())
            .build()

    private fun text(event: Event): String =
        event.content().flatMap { it.parts() }.orElse(emptyList()).first().text().orElse("")

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun configureDatabase(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }
    }
}
