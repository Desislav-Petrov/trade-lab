package org.dpp.tradelab.ledger.controller

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageImpl
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
class LedgerApiDelegateImplGetTransactionsTest(
    @Autowired val mockMvc: MockMvc,
    @MockitoBean val accountService: AccountService,
    @MockitoBean val ledgerService: LedgerService,
) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        val userId    = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val entryId   = UUID.randomUUID()

        val cashEntry = LedgerEntry(
            entryId     = entryId,
            accountId   = accountId,
            type        = EntryType.CREDIT,
            assetType   = AssetType.CASH,
            amount      = BigDecimal("1000.0000"),
            currency    = "USD",
            ticker      = null,
            shares      = null,
            description = "Initial deposit",
            createdAt   = Instant.parse("2026-07-01T10:00:00Z"),
        )

        val stockEntry = LedgerEntry(
            entryId     = UUID.randomUUID(),
            accountId   = accountId,
            type        = EntryType.DEBIT,
            assetType   = AssetType.STOCK_BUY,
            amount      = BigDecimal("500.0000"),
            currency    = "USD",
            ticker      = "AAPL",
            shares      = BigDecimal("2.5000"),
            description = "Buy AAPL",
            createdAt   = Instant.parse("2026-07-02T09:30:00Z"),
        )

        test("getAccountTransactions_validOwner_returns200WithTransactionList") {
            val page = PageImpl(listOf(cashEntry))
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
                .andExpect(jsonPath("$.transactions[0].description").value("Initial deposit"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalCount").value(1))
        }

        test("getAccountTransactions_stockEntry_returns200WithTickerAndShares") {
            val page = PageImpl(listOf(stockEntry))
            whenever(ledgerService.getTransactions(any(), eq(userId), eq(0), eq(25)))
                .thenReturn(page)

            mockMvc.perform(
                get("/api/v1/accounts/$accountId/transactions")
                    .param("userId", userId.toString())
                    .param("page", "0")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.transactions[0].type").value("DEBIT"))
                .andExpect(jsonPath("$.transactions[0].assetType").value("STOCK_BUY"))
                .andExpect(jsonPath("$.transactions[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.transactions[0].shares").value(2.5))
        }

        test("getAccountTransactions_emptyPage_returns200WithEmptyList") {
            whenever(ledgerService.getTransactions(eq(accountId), eq(userId), eq(0), eq(25)))
                .thenReturn(PageImpl(emptyList()))

            mockMvc.perform(
                get("/api/v1/accounts/$accountId/transactions")
                    .param("userId", userId.toString())
                    .param("page", "0")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.transactions").isArray)
                .andExpect(jsonPath("$.transactions").isEmpty)
                .andExpect(jsonPath("$.totalCount").value(0))
        }

        test("getAccountTransactions_accountNotFound_returns404") {
            whenever(ledgerService.getTransactions(eq(accountId), eq(userId), eq(0), eq(25)))
                .thenThrow(AccountNotFoundException(accountId))

            mockMvc.perform(
                get("/api/v1/accounts/$accountId/transactions")
                    .param("userId", userId.toString())
                    .param("page", "0")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.status").value(404))
        }

        test("getAccountTransactions_wrongOwner_returns403") {
            whenever(ledgerService.getTransactions(eq(accountId), eq(userId), eq(0), eq(25)))
                .thenThrow(AccountOwnershipException(accountId))

            mockMvc.perform(
                get("/api/v1/accounts/$accountId/transactions")
                    .param("userId", userId.toString())
                    .param("page", "0")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
        }

        test("getAccountTransactions_pageParam_passedThrough") {
            val page2 = PageImpl(
                listOf(cashEntry),
                org.springframework.data.domain.PageRequest.of(2, 25),
                50,
            )
            whenever(ledgerService.getTransactions(eq(accountId), eq(userId), eq(2), eq(25)))
                .thenReturn(page2)

            mockMvc.perform(
                get("/api/v1/accounts/$accountId/transactions")
                    .param("userId", userId.toString())
                    .param("page", "2")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalCount").value(50))
        }
    }
}
