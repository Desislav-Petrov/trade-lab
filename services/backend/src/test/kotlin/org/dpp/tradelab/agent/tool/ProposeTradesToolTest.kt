package org.dpp.tradelab.agent.tool

import com.google.adk.agents.InvocationContext
import com.google.adk.agents.RunConfig
import com.google.adk.events.EventActions
import com.google.adk.sessions.InMemorySessionService
import com.google.adk.sessions.Session
import com.google.adk.tools.ToolContext
import com.google.genai.types.Type
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.agent.service.TradeOrderInput
import org.dpp.tradelab.agent.service.TradeProposalService
import org.mockito.kotlin.mock
import java.util.UUID

class ProposeTradesToolTest : FunSpec({
    val proposalService = TradeProposalService()
    val tool = ProposeTradesTool(proposalService)
    val conversationId = UUID.randomUUID()
    val authenticatedUser = UUID.randomUUID()
    val selectedAccount = UUID.randomUUID()
    val sessionService = InMemorySessionService()

    fun toolContext(
        stateUserId: UUID = authenticatedUser,
        stateAccountId: UUID = selectedAccount,
        sessionUserId: UUID = stateUserId
    ): ToolContext {
        val session = Session.Builder(conversationId.toString())
            .appName("trade-lab-agent")
            .userId(sessionUserId.toString())
            .state(
                mapOf(
                    "userId" to stateUserId.toString(),
                    "accountId" to stateAccountId.toString()
                )
            )
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

    test("proposeTrades_validBatch_storesProposalInSessionAndReturnsTargetScope") {
        val context = toolContext()
        val result = tool.proposeTrades(
            context,
            listOf(
                TradeOrderInput("AAPL", "BUY", "2"),
                TradeOrderInput("TSLA", "SELL", "1")
            )
        )

        result.status shouldBe "proposed"
        result.targetAccountId shouldBe selectedAccount
        result.orders shouldHaveSize 2
        result.invalidOrders shouldHaveSize 0
        result.proposalId shouldBe proposalService.getProposal(context.state())?.proposalId
    }

    test("proposeTrades_invalidQuantity_omitsOnlyInvalidLine") {
        val result = tool.proposeTrades(
            toolContext(),
            listOf(
                TradeOrderInput("AAPL", "BUY", "2"),
                TradeOrderInput("TSLA", "SELL", "0")
            )
        )

        result.status shouldBe "proposed"
        result.orders.map { it.ticker } shouldBe listOf("AAPL")
        result.invalidOrders shouldHaveSize 1
        result.invalidOrders.single().reason shouldBe "Quantity must be greater than zero."
    }

    test("proposeTrades_nonNumericQuantity_omitsOnlyInvalidLine") {
        val result = tool.proposeTrades(
            toolContext(),
            listOf(
                TradeOrderInput("AAPL", "BUY", "2"),
                TradeOrderInput("TSLA", "SELL", "a lot")
            )
        )

        result.status shouldBe "proposed"
        result.orders.map { it.ticker } shouldBe listOf("AAPL")
        result.invalidOrders shouldHaveSize 1
        result.invalidOrders.single().reason shouldBe "Quantity must be a number."
    }

    test("proposeTrades_scopeMismatch_failsClosed") {
        shouldThrow<IllegalStateException> {
            tool.proposeTrades(
                toolContext(sessionUserId = UUID.randomUUID()),
                listOf(TradeOrderInput("AAPL", "BUY", "1"))
            )
        }
    }

    test("asFunctionTool_createsNamedFunctionTool") {
        val declaration = tool.asFunctionTool().declaration().get()
        declaration.name().orElseThrow() shouldBe "proposeTrades"
        val orderSchema = declaration.parameters().orElseThrow()
            .properties().orElseThrow()["orders"]!!
        val orderProperties = orderSchema.items().orElseThrow().properties().orElseThrow()
        orderProperties.keys shouldContainAll setOf("ticker", "side", "quantity")
        orderProperties["quantity"]!!.type().orElseThrow().knownEnum() shouldBe Type.Known.STRING
    }

    test("asFunctionTool_convertsStructuredOrderLines") {
        val functionTool = tool.asFunctionTool()
        val args = mapOf(
            "orders" to listOf(
                mapOf("ticker" to "AAPL", "side" to "BUY", "quantity" to 2)
            )
        )
        val context = toolContext()
        val result = functionTool.runAsync(args, context).blockingGet()

        result["status"] shouldBe "proposed"
    }
})
