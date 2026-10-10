package org.dpp.tradelab.agent.service

import com.google.adk.events.Event
import com.google.adk.events.EventActions
import com.google.adk.sessions.GetSessionConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.agent.repository.AgentEventRepository
import org.dpp.tradelab.agent.repository.AgentSessionRepository
import org.dpp.tradelab.agent.repository.AgentUserStateRepository
import org.dpp.tradelab.config.AGENT_APP_NAME
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PersistentSessionServiceH2Test(
    @Autowired private val service: PersistentSessionService,
    @Autowired private val sessions: AgentSessionRepository,
    @Autowired private val events: AgentEventRepository,
    @Autowired private val userStates: AgentUserStateRepository,
    @Autowired private val serializer: AdkJsonSerializer
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    init {
        beforeEach {
            events.deleteAll()
            sessions.deleteAll()
            userStates.deleteAll()
        }

        test("sessionLifecycle_persistsStateEventsOrderingListAndDelete") {
            val userId = UUID.randomUUID().toString()
            val conversationId = UUID.randomUUID().toString()
            val initialState = ConcurrentHashMap<String, Any>().apply {
                put("accountId", "account-1")
                put("user:prior", "shared value")
            }
            val created = service.createSession(AGENT_APP_NAME, userId, initialState, conversationId).blockingGet()
            service.appendEvent(created, event("first", 1)).blockingGet()
            service.appendEvent(created, event("second", 2)).blockingGet()

            val restartedService = PersistentSessionService(sessions, events, userStates, serializer)
            val restored = restartedService.getSession(
                AGENT_APP_NAME,
                userId,
                conversationId,
                Optional.empty()
            ).blockingGet()!!

            restored.state()["accountId"] shouldBe "account-1"
            restored.state()["user:prior"] shouldBe "shared value"
            restored.events().map { it.content().flatMap { content -> content.parts() }.orElse(emptyList()).first().text().orElse("") } shouldContainExactly
                listOf("first", "second")
            events.findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc(AGENT_APP_NAME, userId, conversationId)
                .map { it.sequence } shouldContainExactly listOf(1L, 2L)
            restartedService.getSession(
                AGENT_APP_NAME,
                userId,
                conversationId,
                Optional.of(GetSessionConfig.builder().numRecentEvents(1).build())
            ).blockingGet()!!.events().size shouldBe 1
            restartedService.listSessions(AGENT_APP_NAME, userId).blockingGet().sessionIds() shouldContainExactly
                listOf(conversationId)

            service.deleteSession(AGENT_APP_NAME, userId, conversationId).blockingAwait()
            sessions.findByAppNameAndUserIdAndSessionId(AGENT_APP_NAME, userId, conversationId) shouldBe null
            events.findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc(AGENT_APP_NAME, userId, conversationId) shouldBe emptyList()
        }

        test("appendEvent_userStateDeltaIsSharedAcrossSessionsButIsolatedByUser") {
            val userId = UUID.randomUUID().toString()
            val otherUserId = UUID.randomUUID().toString()
            val firstConversationId = UUID.randomUUID().toString()
            val secondConversationId = UUID.randomUUID().toString()
            val otherConversationId = UUID.randomUUID().toString()
            val first = service.createSession(AGENT_APP_NAME, userId, ConcurrentHashMap(), firstConversationId).blockingGet()
            val stateDelta = mapOf("user:last_conversation" to "remember this")
            service.appendEvent(first, event("summary update", 3, stateDelta)).blockingGet()
            service.createSession(AGENT_APP_NAME, userId, ConcurrentHashMap(), secondConversationId).blockingGet()
            service.createSession(AGENT_APP_NAME, otherUserId, ConcurrentHashMap(), otherConversationId).blockingGet()

            service.getSession(AGENT_APP_NAME, userId, secondConversationId, Optional.empty())
                .blockingGet()!!.state()["user:last_conversation"] shouldBe "remember this"
            service.getSession(AGENT_APP_NAME, otherUserId, otherConversationId, Optional.empty())
                .blockingGet()!!.state().containsKey("user:last_conversation") shouldBe false
        }
    }

    private fun event(text: String, timestampSeconds: Long, stateDelta: Map<String, Any> = emptyMap()): Event {
        val actions = EventActions.builder().stateDelta(stateDelta).build()
        return Event.builder()
            .id(UUID.randomUUID().toString())
            .author("assistant")
            .actions(actions)
            .content(com.google.genai.types.Content.fromParts(com.google.genai.types.Part.fromText(text)))
            .timestamp(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(timestampSeconds).toEpochMilli())
            .build()
    }
}
