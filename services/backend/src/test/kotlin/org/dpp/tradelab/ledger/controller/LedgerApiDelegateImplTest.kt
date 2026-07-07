package org.dpp.tradelab.ledger.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.dpp.tradelab.ledger.exception.InvalidCurrencyException
import org.dpp.tradelab.ledger.exception.UserNotFoundException
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.AccountStatus
import org.dpp.tradelab.ledger.model.Currency
import org.dpp.tradelab.ledger.service.AccountService
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class LedgerApiDelegateImplTest(
    @Autowired val mockMvc: MockMvc,
    @MockitoBean val accountService: AccountService
) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    private val objectMapper = ObjectMapper()

    init {
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val validAccount = Account(
            accountId = accountId,
            userId = userId,
            name = "My Account",
            balance = BigDecimal.ZERO,
            currency = Currency.USD,
            status = AccountStatus.ACTIVE,
            createdAt = Instant.parse("2026-06-20T12:00:00Z")
        )

        test("openAccount_validRequest_returns201WithAccountResponse") {
            whenever(accountService.openAccount(eq(userId), eq(Currency.USD), eq("My Account")))
                .thenReturn(validAccount)

            mockMvc.perform(
                post("/api/v1/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf(
                        "userId" to userId.toString(),
                        "currency" to "USD",
                        "name" to "My Account"
                    )))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("My Account"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(0))
        }

        test("openAccount_missingUserId_returns400") {
            mockMvc.perform(
                post("/api/v1/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("currency" to "USD")))
            )
                .andExpect(status().isBadRequest)
        }

        test("openAccount_missingCurrency_returns400") {
            mockMvc.perform(
                post("/api/v1/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("userId" to userId.toString())))
            )
                .andExpect(status().isBadRequest)
        }

        test("openAccount_unknownUserId_returns403") {
            whenever(accountService.openAccount(any(), any(), anyOrNull()))
                .thenThrow(UserNotFoundException(userId))

            mockMvc.perform(
                post("/api/v1/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf(
                        "userId" to userId.toString(),
                        "currency" to "USD"
                    )))
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
        }

        test("openAccount_invalidCurrency_returns400") {
            whenever(accountService.openAccount(any(), any(), anyOrNull()))
                .thenThrow(InvalidCurrencyException("XYZ"))

            mockMvc.perform(
                post("/api/v1/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf(
                        "userId" to userId.toString(),
                        "currency" to "USD"
                    )))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.status").value(400))
        }

        test("listAccounts_existingUserId_returns200WithAccountList") {
            whenever(accountService.listAccountsByUser(userId))
                .thenReturn(listOf(validAccount))

            mockMvc.perform(
                get("/api/v1/accounts")
                    .param("userId", userId.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accounts[0].id").value(accountId.toString()))
                .andExpect(jsonPath("$.accounts[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.accounts[0].currency").value("USD"))
        }

        test("listAccounts_noAccounts_returns200WithEmptyList") {
            whenever(accountService.listAccountsByUser(userId))
                .thenReturn(emptyList())

            mockMvc.perform(
                get("/api/v1/accounts")
                    .param("userId", userId.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accounts").isArray)
                .andExpect(jsonPath("$.accounts").isEmpty)
        }

        test("listAccounts_statusActive_returns200WithActiveAccountsOnly") {
            whenever(accountService.listActiveAccountsByUser(userId))
                .thenReturn(listOf(validAccount))

            mockMvc.perform(
                get("/api/v1/accounts")
                    .param("userId", userId.toString())
                    .param("status", "ACTIVE")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accounts[0].id").value(accountId.toString()))
                .andExpect(jsonPath("$.accounts[0].status").value("ACTIVE"))
        }

        test("listAccounts_statusInvalid_returns400WithErrorResponse") {
            mockMvc.perform(
                get("/api/v1/accounts")
                    .param("userId", userId.toString())
                    .param("status", "INVALID")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.status").value(400))
        }
    }
}
