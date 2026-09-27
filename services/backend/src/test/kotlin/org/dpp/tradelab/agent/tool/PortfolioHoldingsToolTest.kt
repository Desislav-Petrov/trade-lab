package org.dpp.tradelab.agent.tool

import com.google.adk.agents.InvocationContext
import com.google.adk.agents.RunConfig
import com.google.adk.events.EventActions
import com.google.adk.sessions.InMemorySessionService
import com.google.adk.sessions.Session
import com.google.adk.tools.ToolContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.portfolio.api.PortfolioApi
import org.dpp.tradelab.portfolio.api.PortfolioHoldingsView
import org.dpp.tradelab.portfolio.exception.PortfolioAccountAccessDeniedException
import org.dpp.tradelab.portfolio.exception.PortfolioAccountNotFoundException
import org.dpp.tradelab.portfolio.exception.PortfolioBalanceUnavailableException
import org.dpp.tradelab.portfolio.exception.PortfolioPriceUnavailableException
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class PortfolioHoldingsToolTest : FunSpec({
    val portfolioApi = mock<PortfolioApi>()
    val tool = PortfolioHoldingsTool(portfolioApi)
    val userId = UUID.randomUUID()
    val accountId = UUID.randomUUID()
    val sessionService = InMemorySessionService()

    fun toolContextFromState(
        state: Map<String, Any>,
        sessionUserId: String = UUID.randomUUID().toString()
    ): ToolContext {
        val session = Session.Builder("session-id")
            .appName("trade-lab-agent")
            .userId(sessionUserId)
            .state(state)
            .build()

        val invocationContext = InvocationContext.builder()
            .invocationId("invocation-id")
            .agent(mock())
            .session(session)
            .sessionService(sessionService)
            .runConfig(
                RunConfig.builder()
                    .streamingMode(RunConfig.StreamingMode.SSE)
                    .autoCreateSession(false)
                    .build()
            )
            .callbackContextData(emptyMap())
            .build()

        return ToolContext.builder(invocationContext)
            .actions(EventActions.builder().build())
            .eventId("event-id")
            .build()
    }

    fun toolContext(
        userId: UUID = UUID.randomUUID(),
        accountId: UUID = UUID.randomUUID()
    ): ToolContext = toolContextFromState(
        state = mapOf(
            "userId" to userId.toString(),
            "accountId" to accountId.toString()
        ),
        sessionUserId = userId.toString()
    )

    fun holdingsView() = PortfolioHoldingsView(
        holdings = listOf(
            PortfolioHoldingsView.StockHolding(
                ticker = "AAPL",
                quantity = BigDecimal("2.0000"),
                currentPrice = BigDecimal("150.0000"),
                currentValue = BigDecimal("300.0000"),
                avgPrice = BigDecimal("140.0000"),
                portfolioPercent = BigDecimal("37.5000"),
                unrealisedPnL = BigDecimal("20.0000")
            )
        ),
        cash = PortfolioHoldingsView.CashHolding(
            balance = BigDecimal("500.0000"),
            currency = "USD",
            portfolioPercent = BigDecimal("62.5000")
        )
    )

    beforeEach {
        org.mockito.kotlin.reset(portfolioApi)
    }

    test("getHoldings_happyPath_returnsPortfolioView") {
        whenever(portfolioApi.getHoldings(accountId, userId)).thenReturn(holdingsView())

        val result = tool.getHoldings(toolContext(userId, accountId))

        result.status shouldBe "ok"
        result.portfolio shouldBe holdingsView()
        result.error shouldBe null
    }

    test("getHoldings_usesSessionScope_notToolArgs") {
        whenever(portfolioApi.getHoldings(accountId, userId)).thenReturn(holdingsView())

        tool.getHoldings(toolContext(userId, accountId))

        verify(portfolioApi).getHoldings(accountId, userId)
        verifyNoMoreInteractions(portfolioApi)
    }

    test("getHoldings_accountNotFound_returnsErrorPayload") {
        whenever(portfolioApi.getHoldings(accountId, userId))
            .thenThrow(PortfolioAccountNotFoundException("Account not found"))

        val result = tool.getHoldings(toolContext(userId, accountId))

        result.status shouldBe "error"
        result.error shouldBe PortfolioHoldingsTool.Error("account_not_found", "Account not found")
        result.portfolio shouldBe null
    }

    test("getHoldings_accountAccessDenied_returnsErrorPayload") {
        whenever(portfolioApi.getHoldings(accountId, userId))
            .thenThrow(PortfolioAccountAccessDeniedException("Access denied"))

        val result = tool.getHoldings(toolContext(userId, accountId))

        result.status shouldBe "error"
        result.error shouldBe PortfolioHoldingsTool.Error("account_access_denied", "Access denied")
        result.portfolio shouldBe null
    }

    test("getHoldings_priceUnavailable_returnsErrorPayload") {
        whenever(portfolioApi.getHoldings(accountId, userId))
            .thenThrow(PortfolioPriceUnavailableException("Price unavailable"))

        val result = tool.getHoldings(toolContext(userId, accountId))

        result.status shouldBe "error"
        result.error shouldBe PortfolioHoldingsTool.Error("price_unavailable", "Price unavailable")
        result.portfolio shouldBe null
    }

    test("getHoldings_balanceUnavailable_returnsErrorPayload") {
        whenever(portfolioApi.getHoldings(accountId, userId))
            .thenThrow(PortfolioBalanceUnavailableException("Balance unavailable"))

        val result = tool.getHoldings(toolContext(userId, accountId))

        result.status shouldBe "error"
        result.error shouldBe PortfolioHoldingsTool.Error("balance_unavailable", "Balance unavailable")
        result.portfolio shouldBe null
    }

    test("getHoldings_missingUserIdInSessionState_throwsIllegalStateException") {
        val thrown = shouldThrow<IllegalStateException> {
            tool.getHoldings(
                toolContextFromState(
                    state = mapOf("accountId" to accountId.toString())
                )
            )
        }

        thrown.message shouldBe "Missing userId in agent session state"
    }

    test("getHoldings_invalidAccountIdInSessionState_throwsIllegalStateException") {
        val thrown = shouldThrow<IllegalStateException> {
            tool.getHoldings(
                toolContextFromState(
                    state = mapOf(
                        "userId" to userId.toString(),
                        "accountId" to "not-a-uuid"
                    ),
                    sessionUserId = userId.toString()
                )
            )
        }

        thrown.message shouldBe "Invalid accountId in agent session state"
    }

    test("getHoldings_invalidUserIdInSessionState_throwsIllegalStateException") {
        val thrown = shouldThrow<IllegalStateException> {
            tool.getHoldings(
                toolContextFromState(
                    state = mapOf(
                        "userId" to "not-a-uuid",
                        "accountId" to accountId.toString()
                    )
                )
            )
        }

        thrown.message shouldBe "Invalid userId in agent session state"
    }

    test("getHoldings_missingAccountIdInSessionState_throwsIllegalStateException") {
        val thrown = shouldThrow<IllegalStateException> {
            tool.getHoldings(
                toolContextFromState(
                    state = mapOf("userId" to userId.toString()),
                    sessionUserId = userId.toString()
                )
            )
        }

        thrown.message shouldBe "Missing accountId in agent session state"
    }

    test("getHoldings_userIdStateMismatch_throwsIllegalStateException") {
        val thrown = shouldThrow<IllegalStateException> {
            tool.getHoldings(
                toolContextFromState(
                    state = mapOf(
                        "userId" to UUID.randomUUID().toString(),
                        "accountId" to accountId.toString()
                    ),
                    sessionUserId = userId.toString()
                )
            )
        }

        thrown.message shouldBe "userId in agent session state does not match authenticated user"
    }
})
