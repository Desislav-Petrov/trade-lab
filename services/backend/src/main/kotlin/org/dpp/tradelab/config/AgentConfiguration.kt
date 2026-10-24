package org.dpp.tradelab.config

import com.google.adk.agents.LlmAgent
import com.google.adk.agents.RunConfig
import com.google.adk.models.Gemini
import com.google.adk.runner.Runner
import com.google.adk.sessions.BaseSessionService
import org.dpp.tradelab.agent.tool.ExecuteTradesTool
import org.dpp.tradelab.agent.tool.PortfolioHoldingsTool
import org.dpp.tradelab.agent.tool.ProposeTradesTool
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

internal const val AGENT_APP_NAME = "trade-lab-agent"

@Configuration
class AgentConfiguration(
    @Value("\${app.agent.model}")
    private val modelName: String,
    @Value("\${app.agent.api-key}")
    private val apiKey: String
) {

    @Bean
    fun agentModel(): Gemini {
        require(modelName.isNotBlank()) { "app.agent.model must be configured" }
        require(apiKey.isNotBlank()) { "app.agent.api-key must be configured" }

        return Gemini(modelName, apiKey)
    }

    @Bean
    fun portfolioAnalystAgent(agentModel: Gemini, portfolioHoldingsTool: PortfolioHoldingsTool): LlmAgent =
        LlmAgent.builder()
            .name("portfolio-analyst")
            .description("Handles portfolio and holdings discussions using the scoped portfolio holdings tool.")
            .model(agentModel)
            .instruction(
                """
                You are the Trade Lab Portfolio Analyst.
                Focus only on portfolio, positions, holdings, allocation, and performance questions.
                Call the portfolio holdings tool to read the authenticated user's real holdings for the conversation's single account before answering account-specific portfolio questions.
                If the tool returns an error, surface that error message to the user.
                """.trimIndent()
            )
            .tools(portfolioHoldingsTool.asFunctionTool())
            .build()

    @Bean
    fun tradeExecutorAgent(
        agentModel: Gemini,
        proposeTradesTool: ProposeTradesTool,
        executeTradesTool: ExecuteTradesTool
    ): LlmAgent =
        LlmAgent.builder()
            .name("trade-executor")
            .description("Proposes and executes confirmed MARKET stock trades through scoped trading tools.")
            .model(agentModel)
            .instruction(
                """
                You are the Trade Lab Trade Executor.
                Handle only requests to buy or sell stocks. Support MARKET orders only; BUY and SELL are order sides.
                Parse each request into an ordered batch of ticker, side (BUY or SELL), and positive quantity values.
                Call proposeTrades to present the proposed lines and target account, then wait for one explicit confirmation
                covering the entire batch. Never place an order before that confirmation.
                If the user clearly declines or cancels, call executeTrades with confirmed=false and report that no orders
                were placed. If the user's answer is ambiguous, ask them to confirm or cancel and do not call executeTrades.
                After a clear confirmation, call executeTrades with confirmed=true once. Use its per-order outcomes as the
                source of truth; continue/report failures individually and state the number filled out of the total.
                Do not claim a trade was filled unless the tool reports FILLED.
                """.trimIndent()
            )
            .tools(proposeTradesTool.asFunctionTool(), executeTradesTool.asFunctionTool())
            .build()

    @Bean
    fun mainAgent(
        agentModel: Gemini,
        portfolioAnalystAgent: LlmAgent,
        tradeExecutorAgent: LlmAgent
    ): LlmAgent =
        LlmAgent.builder()
            .name("trade-lab-assistant")
            .description("Orchestrates assistant conversations and delegates portfolio analysis and trade execution.")
            .model(agentModel)
            .instruction(
                """
                You are the Trade Lab AI Assistant.
                Never perform portfolio analysis yourself.
                Delegate every portfolio, positions, holdings, allocation, or account analysis question to the portfolio-analyst sub-agent.
                Delegate every request to buy or sell stocks to the trade-executor sub-agent. Never place trades yourself.
                Do not claim to have live account-specific portfolio data unless dedicated portfolio tools are introduced.
                If the user-scoped state value {user:user_profile?} is present, use its first whitespace-delimited name token to address the user throughout the conversation. If it is absent or blank, address the user neutrally without inventing a name.
                Handle only general non-portfolio assistant questions directly.
                """.trimIndent()
            )
            .subAgents(portfolioAnalystAgent, tradeExecutorAgent)
            .build()

    @Bean
    fun agentRunConfig(): RunConfig =
        RunConfig.builder()
            .streamingMode(RunConfig.StreamingMode.SSE)
            .autoCreateSession(false)
            .build()

    @Bean
    fun agentRunner(mainAgent: LlmAgent, agentSessionService: BaseSessionService): Runner =
        Runner.builder()
            .appName(AGENT_APP_NAME)
            .agent(mainAgent)
            .sessionService(agentSessionService)
            .build()
}
