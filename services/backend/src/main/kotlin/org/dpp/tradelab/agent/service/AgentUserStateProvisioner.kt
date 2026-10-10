package org.dpp.tradelab.agent.service

import org.dpp.tradelab.agent.model.AgentUserState
import org.dpp.tradelab.agent.repository.AgentUserStateRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Service
class AgentUserStateProvisioner(
    private val agentUserStateRepository: AgentUserStateRepository,
    transactionManager: PlatformTransactionManager
) {
    private val transactionTemplate = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun ensureExists(appName: String, userId: String) {
        try {
            transactionTemplate.executeWithoutResult {
                if (agentUserStateRepository.findByAppNameAndUserIdForUpdate(appName, userId) == null) {
                    agentUserStateRepository.saveAndFlush(
                        AgentUserState(
                            id = UUID.randomUUID(),
                            appName = appName,
                            userId = userId,
                            stateJson = "{}"
                        )
                    )
                }
            }
        } catch (ex: DataIntegrityViolationException) {
            if (agentUserStateRepository.findByAppNameAndUserId(appName, userId) == null) throw ex
        }
    }
}
