package org.dpp.tradelab.agent.tool

import com.google.adk.tools.FunctionTool
import com.google.adk.tools.ToolContext
import org.dpp.tradelab.portfolio.api.PortfolioApi
import org.dpp.tradelab.portfolio.api.PortfolioHoldingsView
import org.dpp.tradelab.portfolio.exception.PortfolioAccountAccessDeniedException
import org.dpp.tradelab.portfolio.exception.PortfolioAccountNotFoundException
import org.dpp.tradelab.portfolio.exception.PortfolioBalanceUnavailableException
import org.dpp.tradelab.portfolio.exception.PortfolioPriceUnavailableException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PortfolioHoldingsTool(
    private val portfolioApi: PortfolioApi
) {
    fun asFunctionTool(): FunctionTool = FunctionTool.create(this, "getHoldings")

    fun getHoldings(toolContext: ToolContext): Result {
        val scope = ToolScope.from(toolContext)

        return try {
            Result(
                status = "ok",
                portfolio = portfolioApi.getHoldings(scope.accountId, scope.userId)
            )
        } catch (ex: PortfolioAccountNotFoundException) {
            portfolioErrorResult(ex)
        } catch (ex: PortfolioAccountAccessDeniedException) {
            portfolioErrorResult(ex)
        } catch (ex: PortfolioPriceUnavailableException) {
            portfolioErrorResult(ex)
        } catch (ex: PortfolioBalanceUnavailableException) {
            portfolioErrorResult(ex)
        }
    }

    data class Result(
        val status: String,
        val portfolio: PortfolioHoldingsView? = null,
        val error: Error? = null
    ) {
        companion object {
            fun error(type: String, message: String?): Result =
                Result(
                    status = "error",
                    error = Error(
                        type = type,
                        message = message ?: "Portfolio data is unavailable."
                    )
                )
        }
    }

    data class Error(
        val type: String,
        val message: String
    )

    private fun portfolioErrorResult(error: RuntimeException): Result = when (error) {
        is PortfolioAccountNotFoundException -> Result.error("account_not_found", error.message)
        is PortfolioAccountAccessDeniedException -> Result.error("account_access_denied", error.message)
        is PortfolioPriceUnavailableException -> Result.error("price_unavailable", error.message)
        is PortfolioBalanceUnavailableException -> Result.error("balance_unavailable", error.message)
        else -> error("Unsupported portfolio exception: ${error::class.java.simpleName}")
    }

    private data class ToolScope(
        val userId: UUID,
        val accountId: UUID
    ) {
        companion object {
            fun from(toolContext: ToolContext): ToolScope {
                val authenticatedUserId = requireAuthenticatedUserId(toolContext)
                val stateUserId = requireUuid(toolContext, "userId")

                if (stateUserId != authenticatedUserId) {
                    throw IllegalStateException("userId in agent session state does not match authenticated user")
                }

                return ToolScope(
                    userId = authenticatedUserId,
                    accountId = requireUuid(toolContext, "accountId")
                )
            }

            private fun requireUuid(toolContext: ToolContext, key: String): UUID {
                val value = toolContext.state()[key]?.toString()
                    ?: error("Missing $key in agent session state")
                return try {
                    UUID.fromString(value)
                } catch (ex: IllegalArgumentException) {
                    throw IllegalStateException("Invalid $key in agent session state", ex)
                }
            }

            private fun requireAuthenticatedUserId(toolContext: ToolContext): UUID =
                try {
                    val value = toolContext.userId()?.takeIf { it.isNotBlank() }
                        ?: toolContext.invocationContext().session()?.userId()?.takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("Missing authenticated userId in agent session")
                    UUID.fromString(value)
                } catch (ex: IllegalArgumentException) {
                    throw IllegalStateException("Invalid authenticated userId in agent session", ex)
                }
        }
    }
}
