package org.dpp.tradelab.config

import com.google.adk.agents.LlmAgent
import com.google.adk.agents.RunConfig
import com.google.adk.models.Gemini
import com.google.adk.runner.Runner
import com.google.adk.sessions.InMemorySessionService
import org.dpp.tradelab.agent.tool.PortfolioHoldingsTool
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
    fun mainAgent(agentModel: Gemini, portfolioAnalystAgent: LlmAgent): LlmAgent =
        LlmAgent.builder()
            .name("trade-lab-assistant")
            .description("Orchestrates assistant conversations and delegates portfolio analysis.")
            .model(agentModel)
            .instruction(
                """
                You are the Trade Lab AI Assistant.
                Never perform portfolio analysis yourself.
                Delegate every portfolio, positions, holdings, allocation, or account analysis question to the portfolio-analyst sub-agent.
                Do not claim to have live account-specific portfolio data unless dedicated portfolio tools are introduced.
                Handle only general non-portfolio assistant questions directly.
                """.trimIndent()
            )
            .subAgents(portfolioAnalystAgent)
            .build()

    @Bean
    fun agentSessionService(): InMemorySessionService = InMemorySessionService()

    @Bean
    fun agentRunConfig(): RunConfig =
        RunConfig.builder()
            .streamingMode(RunConfig.StreamingMode.SSE)
            .autoCreateSession(false)
            .build()

    @Bean
    fun agentRunner(mainAgent: LlmAgent, agentSessionService: InMemorySessionService): Runner =
        Runner.builder()
            .appName(AGENT_APP_NAME)
            .agent(mainAgent)
            .sessionService(agentSessionService)
            .build()
}
