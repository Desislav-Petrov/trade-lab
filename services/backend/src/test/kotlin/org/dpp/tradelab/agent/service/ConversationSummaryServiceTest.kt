package org.dpp.tradelab.agent.service

import com.google.adk.events.Event
import com.google.adk.models.Gemini
import com.google.adk.models.LlmRequest
import com.google.adk.models.LlmResponse
import com.google.adk.sessions.GetSessionConfig
import com.google.adk.sessions.Session
import com.google.adk.sessions.SessionKey
import com.google.genai.types.Content
import com.google.genai.types.Part
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import org.dpp.tradelab.config.AGENT_APP_NAME
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class ConversationSummaryServiceTest : FunSpec({
    val agentModel = mock<Gemini>()
    val sessionService = mock<PersistentSessionService>()
    val summaryService = ConversationSummaryService(agentModel, sessionService)
    val userId = UUID.randomUUID()
    val conversationId = UUID.randomUUID()

    fun conversation(): Session =
        Session.builder(SessionKey(AGENT_APP_NAME, userId.toString(), conversationId.toString()))
            .events(
                listOf(
                    Event.builder().author("user").content(Content.fromParts(Part.fromText("How is my portfolio?"))).build(),
                    Event.builder().author("assistant").content(Content.fromParts(Part.fromText("It is diversified."))).build()
                )
            )
            .build()

    fun stubConversation() {
        whenever(
            sessionService.getSession(
                eq(AGENT_APP_NAME),
                eq(userId.toString()),
                eq(conversationId.toString()),
                any<Optional<GetSessionConfig>>()
            )
        ).thenReturn(Maybe.just(conversation()))
    }

    beforeEach {
        reset(agentModel, sessionService)
    }

    test("regenerate_completedConversation_generatesAndPersistsSummary") {
        stubConversation()
        whenever(agentModel.model()).thenReturn("gemini-test-model")
        whenever(agentModel.generateContent(any<LlmRequest>(), eq(false))).thenReturn(
            Flowable.just(LlmResponse.builder().content(Content.fromParts(Part.fromText("Discussed portfolio goals."))).build())
        )

        summaryService.regenerate(userId, conversationId)

        verify(sessionService).updateUserScopedValue(
            AGENT_APP_NAME,
            userId.toString(),
            "user:last_conversation",
            "Discussed portfolio goals."
        )
    }

    test("regenerate_modelFailure_retainsPreviousSummary") {
        stubConversation()
        whenever(agentModel.model()).thenReturn("gemini-test-model")
        whenever(agentModel.generateContent(any<LlmRequest>(), eq(false))).thenReturn(Flowable.error(IllegalStateException("offline")))

        summaryService.regenerate(userId, conversationId)

        verify(sessionService, never()).updateUserScopedValue(any(), any(), any(), any())
    }

    test("regenerate_stateWriteFailure_doesNotPropagate") {
        stubConversation()
        whenever(agentModel.model()).thenReturn("gemini-test-model")
        whenever(agentModel.generateContent(any<LlmRequest>(), eq(false))).thenReturn(
            Flowable.just(LlmResponse.builder().content(Content.fromParts(Part.fromText("A summary."))).build())
        )
        whenever(sessionService.updateUserScopedValue(any(), any(), any(), any()))
            .thenThrow(IllegalStateException("database unavailable"))

        summaryService.regenerate(userId, conversationId)

        verify(sessionService).updateUserScopedValue(any(), any(), any(), any())
    }

    test("getSummary_valueExists_returnsSummary") {
        whenever(sessionService.getUserScopedValue(AGENT_APP_NAME, userId.toString(), "user:last_conversation"))
            .thenReturn("Previous conversation")

        summaryService.getSummary(userId) shouldBe "Previous conversation"
    }

    test("getSummary_valueMissing_returnsNull") {
        whenever(sessionService.getUserScopedValue(AGENT_APP_NAME, userId.toString(), "user:last_conversation"))
            .thenReturn(null)

        summaryService.getSummary(userId) shouldBe null
    }

    test("getSummary_readFailure_returnsNull") {
        whenever(sessionService.getUserScopedValue(AGENT_APP_NAME, userId.toString(), "user:last_conversation"))
            .thenThrow(IllegalStateException("database unavailable"))

        summaryService.getSummary(userId) shouldBe null
    }
})
