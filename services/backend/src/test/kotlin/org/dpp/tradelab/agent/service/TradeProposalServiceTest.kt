package org.dpp.tradelab.agent.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.agent.service.TradeProposalService.OutcomeStatus
import org.dpp.tradelab.agent.service.TradeProposalService.OrderOutcome
import org.dpp.tradelab.agent.service.TradeProposalService.ProposalStatus
import java.math.BigDecimal
import java.util.UUID

class TradeProposalServiceTest : FunSpec({
    val service = TradeProposalService()
    val state = mutableMapOf<String, Any>()
    val conversationId = UUID.randomUUID()
    val userId = UUID.randomUUID()
    val accountId = UUID.randomUUID()
    val orders = listOf(
        TradeOrderInput("AAPL", "BUY", "2"),
        TradeOrderInput("TSLA", "SELL", "1.5")
    )

    beforeEach { state.clear() }

    test("propose_generatesDifferentIdempotencyKeyForEachLine_andStoresProposal") {
        val proposal = service.propose(state, conversationId, userId, accountId, orders)

        proposal.status shouldBe ProposalStatus.AWAITING_CONFIRMATION
        proposal.conversationId shouldBe conversationId
        proposal.orders shouldHaveSize 2
        proposal.orders.map { it.idempotencyKey }.distinct() shouldHaveSize 2
        service.getPendingProposal(state, conversationId, userId, accountId) shouldBe proposal
    }

    test("propose_newBatchReplacesPriorPendingProposal") {
        val oldProposal = service.propose(state, conversationId, userId, accountId, orders)
        val newProposal = service.propose(state, conversationId, userId, accountId, listOf(orders.first()))

        newProposal.proposalId shouldNotBe oldProposal.proposalId
        newProposal.proposalId shouldBe service.getPendingProposal(state, conversationId, userId, accountId)?.proposalId
        service.getSupersededProposal(state)?.status shouldBe ProposalStatus.SUPERSEDED
        newProposal.status shouldBe ProposalStatus.AWAITING_CONFIRMATION
        newProposal.orders shouldHaveSize 1
        service.getProposal(state)?.proposalId shouldBe newProposal.proposalId
    }

    test("recordOutcome_andConfirm_persistOutcomesAndReturnConfirmedProposalOnRetry") {
        val proposal = service.propose(state, conversationId, userId, accountId, orders)
        val outcome = OrderOutcome(
            status = OutcomeStatus.FILLED,
            orderId = UUID.randomUUID(),
            executionPrice = BigDecimal("10.25"),
            totalAmount = BigDecimal("20.50")
        )

        service.recordOutcome(state, proposal.proposalId, 0, outcome)
        val confirmed = service.confirm(state, proposal.proposalId)

        confirmed.status shouldBe ProposalStatus.CONFIRMED
        confirmed.orders.first().outcome shouldBe outcome
        service.confirm(state, proposal.proposalId) shouldBe confirmed
        service.getProposal(state)?.orders?.first()?.outcome shouldBe outcome
        service.getPendingProposal(state, conversationId, userId, accountId) shouldBe null
    }

    test("decline_awaitingProposal_marksDeclined") {
        val proposal = service.propose(state, conversationId, userId, accountId, orders)

        service.decline(state, proposal.proposalId).status shouldBe ProposalStatus.DECLINED
        service.getPendingProposal(state, conversationId, userId, accountId) shouldBe null
    }

    test("getPendingProposal_scopeMismatch_failsClosed") {
        service.propose(state, conversationId, userId, accountId, orders)

        shouldThrow<IllegalStateException> {
            service.getPendingProposal(state, conversationId, UUID.randomUUID(), accountId)
        }
        shouldThrow<IllegalStateException> {
            service.getPendingProposal(state, conversationId, userId, UUID.randomUUID())
        }
    }

    test("propose_emptyBatch_rejectsProposal") {
        shouldThrow<IllegalArgumentException> {
            service.propose(state, conversationId, userId, accountId, emptyList())
        }
    }
})
