package org.dpp.tradelab.agent.service

import com.google.adk.events.Event
import com.google.adk.events.EventActions
import com.google.adk.sessions.State
import com.google.genai.types.Content
import com.google.genai.types.Part
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.agent.exception.SessionPersistenceException
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class AdkJsonSerializerTest : FunSpec({
    val serializer = AdkJsonSerializer()

    test("serializeEvent_deserializeEvent_preservesEventData") {
        val event = Event.builder()
            .id("event-1")
            .author("assistant")
            .content(Content.fromParts(Part.fromText("A response")))
            .actions(EventActions.builder().stateDelta(mapOf("removed" to State.REMOVED)).build())
            .partial(false)
            .timestamp(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli())
            .build()

        val restored = serializer.deserializeEvent(serializer.serializeEvent(event))

        restored.id() shouldBe event.id()
        restored.author() shouldBe event.author()
        restored.content().flatMap { it.parts() }.orElse(emptyList()).mapNotNull { it.text().orElse(null) } shouldBe listOf("A response")
        restored.partial() shouldBe event.partial()
        restored.timestamp() shouldBe event.timestamp()
        restored.actions().stateDelta()["removed"] shouldBe State.REMOVED
    }

    test("serializeState_deserializeState_roundTripsDataAndOmitsRuntimeValues") {
        val state = mapOf(
            "userId" to UUID.randomUUID().toString(),
            "updatedAt" to Instant.parse("2026-01-01T00:00:00Z"),
            "nested" to mapOf("count" to 3, "items" to listOf("one", "two")),
            "temp:token" to "ephemeral",
            "service" to Any()
        )

        val restored = serializer.deserializeState(serializer.serializeState(state))

        restored.keys shouldBe setOf("userId", "updatedAt", "nested")
        restored["nested"] shouldBe mapOf("count" to 3, "items" to listOf("one", "two"))
        serializer.sanitizeState(mapOf("user:summary" to "remembered", "removed" to State.REMOVED))
            .shouldContain("user:summary", "remembered")
    }

    test("deserializeEvent_invalidJson_throwsSessionPersistenceException") {
        shouldThrow<SessionPersistenceException> {
            serializer.deserializeEvent("{")
        }
    }

    test("serializeEvent_failure_throwsSessionPersistenceException") {
        val event = mock<Event>()
        whenever(event.toJson()).thenThrow(IllegalStateException("serialization failure"))

        shouldThrow<SessionPersistenceException> {
            serializer.serializeEvent(event)
        }
    }
})
