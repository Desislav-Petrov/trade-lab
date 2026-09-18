package org.dpp.tradelab.agent.service

import io.kotest.core.spec.style.FunSpec
import java.util.UUID

class AgentServiceTest : FunSpec({

    val service = AgentService()

    test("evaluatePortfolio_validInput_doesNotThrow") {
        service.evaluatePortfolio(UUID.randomUUID(), UUID.randomUUID())
    }
})
