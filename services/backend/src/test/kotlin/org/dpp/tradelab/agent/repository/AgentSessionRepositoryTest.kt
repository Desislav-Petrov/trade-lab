package org.dpp.tradelab.agent.repository

import org.dpp.tradelab.agent.model.AgentSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class AgentSessionRepositoryTest @Autowired constructor(
    private val agentSessionRepository: AgentSessionRepository
) {
    @Test
    fun sessionFinders_scopeAndDeleteBySession() {
        val session = saveSession("app", "user", "session")
        saveSession("app", "other-user", "other-session")

        assertEquals(session.id, agentSessionRepository.findByAppNameAndUserIdAndSessionId("app", "user", "session")?.id)
        assertEquals(session.id, agentSessionRepository.findByAppNameAndUserIdAndSessionIdForUpdate("app", "user", "session")?.id)
        assertEquals(listOf(session.id), agentSessionRepository.findAllByAppNameAndUserId("app", "user").map { it.id })
        assertEquals(1, agentSessionRepository.deleteByAppNameAndUserIdAndSessionId("app", "user", "session"))
        assertNull(agentSessionRepository.findByAppNameAndUserIdAndSessionId("app", "user", "session"))
        assertNotNull(agentSessionRepository.findByAppNameAndUserIdAndSessionId("app", "other-user", "other-session"))
    }

    private fun saveSession(appName: String, userId: String, sessionId: String): AgentSession =
        agentSessionRepository.save(
            AgentSession(
                id = UUID.randomUUID(),
                appName = appName,
                userId = userId,
                sessionId = sessionId,
                stateJson = "{}"
            )
        )
}
