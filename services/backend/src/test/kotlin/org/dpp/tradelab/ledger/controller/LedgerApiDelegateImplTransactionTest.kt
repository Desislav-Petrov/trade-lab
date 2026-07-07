package org.dpp.tradelab.ledger.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.ledger.exception.AccountOwnershipException
import org.dpp.tradelab.ledger.model.AssetType
import org.dpp.tradelab.ledger.model.EntryType
import org.dpp.tradelab.ledger.model.LedgerEntry
import org.dpp.tradelab.ledger.service.AccountService
import org.dpp.tradelab.ledger.service.LedgerService
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class LedgerApiDelegateImplTransactionTest(
    @Autowired val mockMvc: MockMvc,
    @MockitoBean val accountService: AccountService,
    @MockitoBean val ledgerService: LedgerService
) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        val accountId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val entryId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")

        val entry = LedgerEntry(
            entryId = entryId,
            accountId = accountId,
            type = EntryType.CREDIT,
            assetType = AssetType.CASH,
            amount = BigDecimal("1000.0000"),
            currency = "USD",
            ticker = null,
            shares = null,
            description = "Top-up",
            createdAt = Instant.parse("2026-06-24T12:00:00Z")
        )

        test("getAccountTransactions_validRequest_returns200WithTransactionList") {
            val page = PageImpl(listOf(entry), PageRequest.of(0, 25), 1L)
            whenever(ledgerService.getTransactions(eq(accountId), eq(userId), eq(0), eq(25)))
                .thenReturn(page)

            mockMvc.perform(
                get("/api/v1/accounts/$accountId/transactions")
                    .param("userId", userId.toString())
                    .param("page", "0")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.transactions").isArray)
                .andExpect(jsonPath("$.transactions[0].id").value(entryId.toString()))
                .andExpect(jsonPath("$.transactions[0].type").value("CREDIT"))
                .andExpect(jsonPath("$.transactions[0].assetType").value("CASH"))
                .andExpect(jsonPath("$.transactions[0].amount").value(1000.0))
                .andExpect(jsonPath("$.transactions[0].currency").value("USD"))
                .andExpect(jsonPath("$.transactions[0].description").value("Top-up"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalCount").value(1))
        }

        test("getAccountTransactions_accountNotFound_returns404") {
            whenever(ledgerService.getTransactions(any(), any(), any(), any()))
                .thenThrow(AccountNotFoundException(accountId))

            mockMvc.perform(
                get("/api/v1/accounts/$accountId/transactions")
                    .param("userId", userId.toString())
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.status").value(404))
        }

        test("getAccountTransactions_ownershipMismatch_returns403") {
            whenever(ledgerService.getTransactions(any(), any(), any(), any()))
                .thenThrow(AccountOwnershipException(accountId))

            mockMvc.perform(
                get("/api/v1/accounts/$accountId/transactions")
                    .param("userId", userId.toString())
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
        }
    }
}
