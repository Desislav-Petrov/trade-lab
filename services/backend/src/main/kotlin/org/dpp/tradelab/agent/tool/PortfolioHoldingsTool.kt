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

    fun getHoldings(
        request: Request,
        toolContext: ToolContext
    ): Result {
        val scope = ToolScope.from(toolContext)

        return try {
            Result(
                status = "ok",
                portfolio = portfolioApi.getHoldings(scope.accountId, scope.userId)
            )
        } catch (ex: PortfolioAccountNotFoundException) {
            Result.error("account_not_found", ex.message)
        } catch (ex: PortfolioAccountAccessDeniedException) {
            Result.error("account_access_denied", ex.message)
        } catch (ex: PortfolioPriceUnavailableException) {
            Result.error("price_unavailable", ex.message)
        } catch (ex: PortfolioBalanceUnavailableException) {
            Result.error("balance_unavailable", ex.message)
        }
    }

    data class Request(
        val ignored: String? = null
    )

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

    private data class ToolScope(
        val userId: UUID,
        val accountId: UUID
    ) {
        companion object {
            fun from(toolContext: ToolContext): ToolScope =
                ToolScope(
                    userId = requireUuid(toolContext, "userId"),
                    accountId = requireUuid(toolContext, "accountId")
                )

            private fun requireUuid(toolContext: ToolContext, key: String): UUID {
                val value = toolContext.state()[key]?.toString()
                    ?: error("Missing $key in agent session state")
                return try {
                    UUID.fromString(value)
                } catch (ex: IllegalArgumentException) {
                    throw IllegalStateException("Invalid $key in agent session state: $value", ex)
                }
            }
        }
    }
}
