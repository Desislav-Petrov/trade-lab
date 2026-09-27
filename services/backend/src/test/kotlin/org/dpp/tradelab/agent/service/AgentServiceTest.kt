package org.dpp.tradelab.agent.service

import com.google.adk.agents.RunConfig
import com.google.adk.events.Event
import com.google.adk.runner.Runner
import com.google.adk.sessions.InMemorySessionService
import com.google.adk.sessions.Session
import com.google.adk.sessions.SessionKey
import com.google.genai.types.Content
import com.google.genai.types.Part
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import org.dpp.tradelab.agent.exception.AgentUnavailableException
import org.dpp.tradelab.config.AGENT_APP_NAME
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class AgentServiceTest : FunSpec({
    val agentRunner = mock<Runner>()
    val agentSessionService = mock<InMemorySessionService>()
    val agentRunConfig = RunConfig.builder()
        .streamingMode(RunConfig.StreamingMode.SSE)
        .build()
    val agentService = AgentService(agentRunner, agentSessionService, agentRunConfig, timeoutSeconds = 30)

    val userId = UUID.randomUUID()
    val accountId = UUID.randomUUID()
    val conversationId = UUID.randomUUID()

    fun eventWithText(text: String): Event =
        Event.builder()
            .content(Content.fromParts(Part.fromText(text)))
            .build()

    fun partialEventWithText(text: String): Event =
        Event.builder()
            .partial(true)
            .content(Content.fromParts(Part.fromText(text)))
            .build()

    fun functionCallEvent(): Event =
        Event.builder()
            .content(
                Content.fromParts(
                    Part.fromFunctionCall("transfer_to_agent", mapOf<String, Any>("agent_name" to "portfolio-analyst"))
                )
            )
            .build()

    fun functionResponseEvent(): Event =
        Event.builder()
            .content(
                Content.fromParts(
                    Part.fromFunctionResponse(
                        "getHoldings",
                        mapOf<String, Any>("status" to "ok", "portfolio" to mapOf<String, Any>("holdings" to emptyList<Any>()))
                    )
                )
            )
            .build()

    fun session(sessionId: String = conversationId.toString()): Session =
        mock<Session>().also {
            whenever(it.sessionKey()).thenReturn(SessionKey(AGENT_APP_NAME, userId.toString(), sessionId))
        }

    // NOTE: build the session mock into a local val BEFORE the outer whenever(...).
    // session() itself calls whenever(...) to stub sessionKey(); inlining it inside
    // thenReturn(...) starts a nested stubbing and trips Mockito's
    // UnfinishedStubbingException.
    fun stubExistingSession() {
        val existingSession = session()
        whenever(
            agentSessionService.getSession(
                eq(AGENT_APP_NAME),
                eq(userId.toString()),
                eq(conversationId.toString()),
                any()
            )
        ).thenReturn(Maybe.just(existingSession))
    }

    beforeEach {
        reset(agentRunner, agentSessionService)
    }

    test("query_firstTurn_createsSessionAndStreamsChunks") {
        val createdSession = session()
        whenever(
            agentSessionService.getSession(
                eq(AGENT_APP_NAME),
                eq(userId.toString()),
                eq(conversationId.toString()),
                any()
            )
        ).thenReturn(Maybe.empty())
        whenever(
            agentSessionService.createSession(
                eq(AGENT_APP_NAME),
                eq(userId.toString()),
                any<java.util.concurrent.ConcurrentMap<String, Any>>(),
                eq(conversationId.toString())
            )
        ).thenReturn(Single.just(createdSession))
        whenever(agentRunner.runAsync(any<SessionKey>(), any(), any<RunConfig>()))
            .thenReturn(Flowable.just(eventWithText("Hello"), eventWithText(" world")))

        val result = agentService.query(userId, accountId, conversationId, "Hi").toList().blockingGet()

        result shouldContainExactly listOf("Hello", " world")
        verify(agentSessionService).createSession(any(), any(), any(), any())
    }

    test("query_existingSession_reusesSession") {
        val existingSession = session()
        whenever(
            agentSessionService.getSession(
                eq(AGENT_APP_NAME),
                eq(userId.toString()),
                eq(conversationId.toString()),
                any()
            )
        ).thenReturn(Maybe.just(existingSession))
        whenever(agentRunner.runAsync(any<SessionKey>(), any(), any<RunConfig>()))
            .thenReturn(Flowable.just(eventWithText("Reused")))

        val result = agentService.query(userId, accountId, conversationId, "Hi again").toList().blockingGet()

        result shouldContainExactly listOf("Reused")
        verify(agentSessionService, never()).createSession(any(), any(), any(), any())
    }

    test("query_blankEventContent_filtersEmptyChunks") {
        stubExistingSession()
        whenever(agentRunner.runAsync(any<SessionKey>(), any(), any<RunConfig>()))
            .thenReturn(Flowable.just(eventWithText(""), eventWithText("Answer")))

        val result = agentService.query(userId, accountId, conversationId, "Hi").toList().blockingGet()

        result shouldContainExactly listOf("Answer")
    }

    test("query_functionCallAndResponseEvents_areNotSurfacedToClient") {
        stubExistingSession()
        // A delegated tool-calling turn: transfer to sub-agent, tool call, raw
        // tool payload, then the model's natural-language answer. Only the last
        // must reach the client.
        whenever(agentRunner.runAsync(any<SessionKey>(), any(), any<RunConfig>()))
            .thenReturn(
                Flowable.just(
                    functionCallEvent(),
                    functionResponseEvent(),
                    eventWithText("Your portfolio is 85% cash.")
                )
            )

        val result = agentService.query(userId, accountId, conversationId, "How am I doing?").toList().blockingGet()

        result shouldContainExactly listOf("Your portfolio is 85% cash.")
    }

    test("query_partialDeltasThenFinalAggregate_emitsDeltasOnceWithoutDuplication") {
        stubExistingSession()
        // SSE streaming: token deltas (partial=true) followed by the final
        // non-partial aggregate that repeats the whole turn's text.
        whenever(agentRunner.runAsync(any<SessionKey>(), any(), any<RunConfig>()))
            .thenReturn(
                Flowable.just(
                    partialEventWithText("Your portfolio "),
                    partialEventWithText("is 85% cash."),
                    eventWithText("Your portfolio is 85% cash.")
                )
            )

        val result = agentService.query(userId, accountId, conversationId, "How am I doing?").toList().blockingGet()

        result shouldContainExactly listOf("Your portfolio ", "is 85% cash.")
    }

    test("query_runnerFailure_surfacesAgentUnavailableException") {
        stubExistingSession()
        whenever(agentRunner.runAsync(any<SessionKey>(), any(), any<RunConfig>()))
            .thenReturn(Flowable.error(IllegalStateException("boom")))

        shouldThrow<AgentUnavailableException> {
            agentService.query(userId, accountId, conversationId, "Hi").blockingFirst()
        }.message shouldBe "The AI assistant is temporarily unavailable."
    }

    test("query_modelStallsBeyondTimeout_surfacesAgentUnavailableException") {
        val stallingService = AgentService(agentRunner, agentSessionService, agentRunConfig, timeoutSeconds = 1)
        stubExistingSession()
        // Model never emits a chunk within the timeout window.
        whenever(agentRunner.runAsync(any<SessionKey>(), any(), any<RunConfig>()))
            .thenReturn(Flowable.never())

        shouldThrow<AgentUnavailableException> {
            stallingService.query(userId, accountId, conversationId, "Hi").blockingFirst()
        }.message shouldBe "The AI assistant is temporarily unavailable."
    }
})
