package org.dpp.tradelab.config

import com.google.adk.agents.RunConfig
import com.google.adk.runner.Runner
import com.google.adk.sessions.BaseSessionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.dpp.tradelab.agent.repository.AgentEventRepository
import org.dpp.tradelab.agent.repository.AgentSessionRepository
import org.dpp.tradelab.agent.repository.AgentUserStateRepository
import org.dpp.tradelab.agent.service.AdkJsonSerializer
import org.dpp.tradelab.agent.service.PersistentSessionService
import org.dpp.tradelab.agent.service.TradeProposalService
import org.dpp.tradelab.agent.tool.ExecuteTradesTool
import org.dpp.tradelab.agent.tool.PortfolioHoldingsTool
import org.dpp.tradelab.agent.tool.ProposeTradesTool
import org.dpp.tradelab.stocktrading.api.StockTradingApi
import org.dpp.tradelab.portfolio.api.PortfolioApi
import org.mockito.kotlin.mock

class AgentConfigurationTest : FunSpec({
    val configuration = AgentConfiguration(
        modelName = "gemini-test-model",
        apiKey = "test-api-key"
    )
    val portfolioHoldingsTool = PortfolioHoldingsTool(mock<PortfolioApi>())
    val tradeProposalService = TradeProposalService()
    val proposeTradesTool = ProposeTradesTool(tradeProposalService)
    val executeTradesTool = ExecuteTradesTool(tradeProposalService, mock<StockTradingApi>())
    val persistentSessionService = PersistentSessionService(
        mock<AgentSessionRepository>(),
        mock<AgentEventRepository>(),
        mock<AgentUserStateRepository>(),
        AdkJsonSerializer(),
        mock()
    )

    test("agentRunConfig_streamingModeSse_returnsSseConfig") {
        val runConfig = configuration.agentRunConfig()

        runConfig.streamingMode() shouldBe RunConfig.StreamingMode.SSE
    }

    test("agentRunner_usesPersistentSessionService") {
        val model = configuration.agentModel()
        val portfolioAnalyst = configuration.portfolioAnalystAgent(model, portfolioHoldingsTool)
        val tradeExecutor = configuration.tradeExecutorAgent(model, proposeTradesTool, executeTradesTool)
        val runner = configuration.agentRunner(
            configuration.mainAgent(model, portfolioAnalyst, tradeExecutor),
            persistentSessionService
        )

        runner.shouldBeInstanceOf<Runner>()
        runner.sessionService() shouldBe persistentSessionService
        runner.sessionService().shouldBeInstanceOf<BaseSessionService>()
    }

    test("portfolioAnalystAgent_registersHoldingsTool_andHasPortfolioInstruction") {
        val agent = configuration.portfolioAnalystAgent(configuration.agentModel(), portfolioHoldingsTool)

        agent.tools().blockingGet() shouldHaveSize 1
        agent.instruction().toString() shouldContain "Call the portfolio holdings tool"
        agent.instruction().toString() shouldContain "surface that error message"
    }

    test("tradeExecutorAgent_registersProposalAndExecutionTools_andRequiresConfirmation") {
        val model = configuration.agentModel()
        val tradeExecutor = configuration.tradeExecutorAgent(model, proposeTradesTool, executeTradesTool)

        tradeExecutor.name() shouldBe "trade-executor"
        tradeExecutor.tools().blockingGet() shouldHaveSize 2
        tradeExecutor.instruction().toString() shouldContain "wait for one explicit confirmation"
        tradeExecutor.instruction().toString() shouldContain "continue/report failures individually"
    }

    test("mainAgent_registersBothSubAgents_andDelegatesTradesAndPortfolioAnalysis") {
        val model = configuration.agentModel()
        val portfolioAnalystAgent = configuration.portfolioAnalystAgent(model, portfolioHoldingsTool)
        val tradeExecutor = configuration.tradeExecutorAgent(model, proposeTradesTool, executeTradesTool)
        val mainAgent = configuration.mainAgent(model, portfolioAnalystAgent, tradeExecutor)

        mainAgent.subAgents() shouldHaveSize 2
        mainAgent.subAgents().map { it.name() } shouldBe listOf("portfolio-analyst", "trade-executor")
        mainAgent.instruction().toString() shouldContain "Never perform portfolio analysis yourself"
        mainAgent.instruction().toString() shouldContain "Delegate every portfolio"
        mainAgent.instruction().toString() shouldContain "Delegate every request to buy or sell stocks"
        mainAgent.instruction().toString() shouldContain "{user:user_profile?}"
        mainAgent.instruction().toString() shouldContain "address the user neutrally"
    }
})
