package org.dpp.tradelab.agent.controller

import org.dpp.tradelab.agent.generated.api.AgentApiDelegate
import org.dpp.tradelab.agent.generated.model.EvaluatePortfolioRequest
import org.dpp.tradelab.agent.generated.model.EvaluatePortfolioResponse
import org.dpp.tradelab.agent.service.AgentService
import org.dpp.tradelab.user.exception.InvalidTokenException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AgentApiDelegateImpl(
    private val agentService: AgentService
) : AgentApiDelegate {

    override fun evaluatePortfolio(evaluatePortfolioRequest: EvaluatePortfolioRequest): ResponseEntity<EvaluatePortfolioResponse> {
        val userId = SecurityContextHolder.getContext().authentication?.principal as? UUID
            ?: throw InvalidTokenException("Authentication required")
        val accountId = evaluatePortfolioRequest.accountId

        agentService.evaluatePortfolio(accountId, userId)

        return ResponseEntity.ok(
            EvaluatePortfolioResponse(
                accountId = accountId,
                status = EvaluatePortfolioResponse.Status.NOT_IMPLEMENTED
            )
        )
    }
}
