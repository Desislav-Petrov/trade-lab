package org.dpp.tradelab.agent.repository

import org.dpp.tradelab.agent.model.AgentEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Transactional
class AgentEventRepositoryTest @Autowired constructor(
    private val agentEventRepository: AgentEventRepository
) {
    @Test
    fun eventFinders_orderBySequenceAndDeleteBySession() {
        saveEvent("app", "user", "session", sequence = 2)
        saveEvent("app", "user", "session", sequence = 1)
        saveEvent("app", "user", "other-session", sequence = 1)

        val events = agentEventRepository.findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc("app", "user", "session")
        assertEquals(listOf(1L, 2L), events.map { it.sequence })
        assertEquals(2L, agentEventRepository.findMaxSequence("app", "user", "session"))
        assertEquals(2, agentEventRepository.deleteAllByAppNameAndUserIdAndSessionId("app", "user", "session"))
        assertEquals(emptyList<AgentEvent>(), agentEventRepository.findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc("app", "user", "session"))
        assertNull(agentEventRepository.findMaxSequence("app", "user", "session"))
    }

    private fun saveEvent(appName: String, userId: String, sessionId: String, sequence: Long): AgentEvent =
        agentEventRepository.save(
            AgentEvent(
                id = UUID.randomUUID(),
                appName = appName,
                userId = userId,
                sessionId = sessionId,
                eventJson = """{"sequence":$sequence}""",
                eventTimestamp = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(sequence),
                sequence = sequence
            )
        )
}
