package org.dpp.tradelab.agent.controller

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.reactivex.rxjava3.core.Flowable
import org.dpp.tradelab.agent.exception.AgentUnavailableException
import org.dpp.tradelab.agent.service.AgentService
import org.dpp.tradelab.ledger.api.AccountSummary
import org.dpp.tradelab.ledger.service.LedgerService
import org.dpp.tradelab.user.service.JwtService
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AgentApiDelegateImplTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val jwtService: JwtService,
    @MockitoBean val agentService: AgentService,
    @MockitoBean val ledgerService: LedgerService
) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        val accountId = UUID.randomUUID()
        val conversationId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        fun requestBody(): String =
            """
            {
              "accountId": "$accountId",
              "conversationId": "$conversationId",
              "message": "Summarise my portfolio"
            }
            """.trimIndent()

        fun authenticatedRequest() =
            post("/api/v1/agent/query")
                .header(HttpHeaders.AUTHORIZATION, "Bea" + "rer " + jwtService.issueToken(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody())

        fun stubOwnedAccount() {
            whenever(ledgerService.getAccount(accountId)).thenReturn(
                AccountSummary(
                    id = accountId,
                    userId = userId,
                    currency = "USD",
                    balance = BigDecimal.ZERO,
                    status = "ACTIVE"
                )
            )
        }

        test("queryAgent_streamedSuccess_returnsEventStream") {
            stubOwnedAccount()
            whenever(agentService.query(any(), any(), any(), any()))
                .thenReturn(Flowable.just("Hello", " world"))

            mockMvc.perform(
                authenticatedRequest()
                    .accept(MediaType.TEXT_EVENT_STREAM)
            )
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string("data: Hello\n\ndata:  world\n\n"))
        }

        test("queryAgent_bufferedFallback_returnsJsonReply") {
            stubOwnedAccount()
            whenever(agentService.query(any(), any(), any(), any()))
                .thenReturn(Flowable.just("Buffered", " reply"))

            mockMvc.perform(
                authenticatedRequest()
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("\$.conversationId").value(conversationId.toString()))
                .andExpect(jsonPath("\$.reply").value("Buffered reply"))
        }

        test("queryAgent_streamFailure_returnsErrorEvent") {
            stubOwnedAccount()
            whenever(agentService.query(any(), any(), any(), any()))
                .thenReturn(
                    Flowable.concat(
                        Flowable.just("Partial"),
                        Flowable.error(IllegalStateException("boom"))
                    )
                )

            mockMvc.perform(
                authenticatedRequest()
                    .accept(MediaType.TEXT_EVENT_STREAM)
            )
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: error")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("The AI assistant is temporarily unavailable.")))
        }

        test("queryAgent_emptyBufferedReply_returns503") {
            stubOwnedAccount()
            whenever(agentService.query(any(), any(), any(), any()))
                .thenReturn(Flowable.empty())

            mockMvc.perform(
                authenticatedRequest()
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("\$.status").value(HttpStatus.SERVICE_UNAVAILABLE.value()))
                .andExpect(jsonPath("\$.error").value("Assistant unavailable"))
        }

        test("queryAgent_clientDisconnect_doesNotEmitErrorEvent") {
            stubOwnedAccount()
            whenever(agentService.query(any(), any(), any(), any()))
                .thenReturn(Flowable.error(IOException("broken pipe")))

            mockMvc.perform(
                authenticatedRequest()
                    .accept(MediaType.TEXT_EVENT_STREAM)
            )
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(""))
        }

        test("queryAgent_nestedClientDisconnect_doesNotEmitErrorEvent") {
            stubOwnedAccount()
            whenever(agentService.query(any(), any(), any(), any()))
                .thenReturn(Flowable.error(RuntimeException("wrapper", IOException("broken pipe"))))

            mockMvc.perform(
                authenticatedRequest()
                    .accept(MediaType.TEXT_EVENT_STREAM)
            )
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(""))
        }

        test("queryAgent_accountNotOwned_returns403") {
            whenever(ledgerService.getAccount(accountId)).thenReturn(
                AccountSummary(
                    id = accountId,
                    userId = UUID.randomUUID(),
                    currency = "USD",
                    balance = BigDecimal.ZERO,
                    status = "ACTIVE"
                )
            )

            mockMvc.perform(
                authenticatedRequest()
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("\$.status").value(HttpStatus.FORBIDDEN.value()))
        }

        test("queryAgent_unauthenticated_returns401") {
            mockMvc.perform(
                post("/api/v1/agent/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .content(requestBody())
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("\$.status").value(HttpStatus.UNAUTHORIZED.value()))

            verify(agentService, never()).query(any(), any(), any(), any())
        }

        test("queryAgent_agentFailure_returns503") {
            stubOwnedAccount()
            whenever(agentService.query(any(), any(), any(), any()))
                .thenThrow(AgentUnavailableException("The AI assistant is temporarily unavailable."))

            mockMvc.perform(
                authenticatedRequest()
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("\$.status").value(HttpStatus.SERVICE_UNAVAILABLE.value()))
                .andExpect(jsonPath("\$.error").value("Assistant unavailable"))
        }
    }
}
