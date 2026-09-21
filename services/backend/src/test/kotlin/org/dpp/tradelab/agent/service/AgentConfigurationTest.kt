package org.dpp.tradelab.agent.service

import com.google.adk.agents.RunConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import com.google.adk.sessions.InMemorySessionService

class AgentConfigurationTest : FunSpec({
    val configuration = AgentConfiguration(
        modelName = "gemini-test-model",
        apiKey = "test-api-key"
    )

    test("agentRunConfig_streamingModeSse_returnsSseConfig") {
        val runConfig = configuration.agentRunConfig()

        runConfig.streamingMode() shouldBe RunConfig.StreamingMode.SSE
    }

    test("agentSessionService_returnsInMemorySessionService") {
        configuration.agentSessionService().shouldBeInstanceOf<InMemorySessionService>()
    }

    test("portfolioAnalystAgent_noTools_hasPortfolioInstruction") {
        val agent = configuration.portfolioAnalystAgent(configuration.agentModel())

        agent.tools().blockingGet() shouldHaveSize 0
        agent.instruction().toString() shouldContain "portfolio"
    }

    test("mainAgent_registersPortfolioSubAgent_andForbidsDirectPortfolioAnalysis") {
        val model = configuration.agentModel()
        val portfolioAnalystAgent = configuration.portfolioAnalystAgent(model)
        val mainAgent = configuration.mainAgent(model, portfolioAnalystAgent)

        mainAgent.subAgents() shouldHaveSize 1
        mainAgent.subAgents().first().name() shouldBe "portfolio-analyst"
        mainAgent.instruction().toString() shouldContain "Never perform portfolio analysis yourself"
        mainAgent.instruction().toString() shouldContain "Delegate every portfolio"
    }
})
