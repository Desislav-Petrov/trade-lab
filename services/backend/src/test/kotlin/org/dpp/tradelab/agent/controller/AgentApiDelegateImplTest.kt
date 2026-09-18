package org.dpp.tradelab.agent.controller

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.dpp.tradelab.agent.service.AgentService
import org.dpp.tradelab.user.service.JwtService
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class AgentApiDelegateImplTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val jwtService: JwtService,
    @MockitoBean val agentService: AgentService
) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        test("evaluatePortfolio_happyPath_returns200WithCorrectBody") {
            mockMvc.perform(
                post("/api/v1/agent/evaluatePortfolio")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueToken(userId))
                    .contentType("application/json")
                    .content("""{"accountId":"$accountId"}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.status").value("NOT_IMPLEMENTED"))

            verify(agentService).evaluatePortfolio(accountId, userId)
        }

        test("evaluatePortfolio_unauthenticated_returns401") {
            mockMvc.perform(
                post("/api/v1/agent/evaluatePortfolio")
                    .contentType("application/json")
                    .content("""{"accountId":"$accountId"}""")
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
        }
    }
}
