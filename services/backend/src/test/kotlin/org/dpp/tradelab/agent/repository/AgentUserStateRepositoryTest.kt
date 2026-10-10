package org.dpp.tradelab.agent.repository

import org.dpp.tradelab.agent.model.AgentUserState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class AgentUserStateRepositoryTest @Autowired constructor(
    private val agentUserStateRepository: AgentUserStateRepository
) {
    @Test
    fun stateFinders_scopeByApplicationAndUser() {
        val saved = agentUserStateRepository.save(
            AgentUserState(
                id = UUID.randomUUID(),
                appName = "app",
                userId = "user",
                stateJson = """{"last_conversation":"summary"}"""
            )
        )

        assertEquals(saved.id, agentUserStateRepository.findByAppNameAndUserId("app", "user")?.id)
        assertEquals(saved.id, agentUserStateRepository.findByAppNameAndUserIdForUpdate("app", "user")?.id)
        assertNull(agentUserStateRepository.findByAppNameAndUserId("app", "other-user"))
    }
}
