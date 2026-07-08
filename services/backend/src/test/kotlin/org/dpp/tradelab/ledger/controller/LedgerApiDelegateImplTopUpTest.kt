package org.dpp.tradelab.ledger.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.dpp.tradelab.ledger.exception.AccountNotActiveException
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.AccountStatus
import org.dpp.tradelab.ledger.model.AssetType
import org.dpp.tradelab.ledger.model.Currency
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
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class LedgerApiDelegateImplTopUpTest(
    @Autowired val mockMvc: MockMvc,
    @MockitoBean val accountService: AccountService,
    @MockitoBean val ledgerService: LedgerService,
) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    private val objectMapper = ObjectMapper()

    init {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        val accountId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val entryId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")

        val savedAccount = Account(
            accountId = accountId,
            userId = userId,
            name = "My Account",
            balance = BigDecimal("1100.0000"),
            currency = Currency.USD,
            status = AccountStatus.ACTIVE,
            createdAt = Instant.parse("2026-06-20T12:00:00Z")
        )
        val savedEntry = LedgerEntry(
            entryId = entryId,
            accountId = accountId,
            type = EntryType.CREDIT,
            assetType = AssetType.CASH,
            amount = BigDecimal("1000"),
            currency = "USD",
            description = "Top-up"
        )

        val validRequestBody = objectMapper.writeValueAsString(
            mapOf("userId" to userId.toString(), "amount" to 1000)
        )

        test("topUpAccount_validRequest_returns200WithFullResponseBody") {
            whenever(accountService.topUpAccount(eq(accountId), eq(userId), eq(BigDecimal("1000"))))
                .thenReturn(Pair(savedAccount, savedEntry))

            mockMvc.perform(
                post("/api/v1/accounts/\$accountId/top-up")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequestBody)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("\$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("\$.newBalance").value(1100.0))
                .andExpect(jsonPath("\$.currency").value("USD"))
                .andExpect(jsonPath("\$.ledgerEntryId").value(entryId.toString()))
                .andExpect(jsonPath("\$.timestamp").isNotEmpty)
        }

        test("topUpAccount_accountNotFound_returns404") {
            whenever(accountService.topUpAccount(any(), any(), any()))
                .thenThrow(AccountNotFoundException(accountId))

            mockMvc.perform(
                post("/api/v1/accounts/\$accountId/top-up")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequestBody)
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("\$.status").value(404))
        }

        test("topUpAccount_accountNotActive_returns403") {
            whenever(accountService.topUpAccount(any(), any(), any()))
                .thenThrow(AccountNotActiveException(accountId))

            mockMvc.perform(
                post("/api/v1/accounts/\$accountId/top-up")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequestBody)
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("\$.status").value(403))
        }

        test("topUpAccount_illegalArgument_returns400") {
            whenever(accountService.topUpAccount(any(), any(), any()))
                .thenThrow(IllegalArgumentException("amount must be greater than zero"))

            mockMvc.perform(
                post("/api/v1/accounts/\$accountId/top-up")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequestBody)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("\$.status").value(400))
        }
    }
}
