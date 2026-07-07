package org.dpp.tradelab.ledger.exception

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.dpp.tradelab.ledger.exception.AccountNotActiveException
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.ledger.exception.AccountOwnershipException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class AccountNotFoundTestController {
    @GetMapping("/test/account-not-found")
    fun trigger(): String {
        throw AccountNotFoundException(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    }
}

@RestController
class AccountNotActiveTestController {
    @GetMapping("/test/account-not-active")
    fun trigger(): String {
        throw AccountNotActiveException(UUID.fromString("00000000-0000-0000-0000-000000000002"))
    }
}

@RestController
class AccountOwnershipTestController {
    @GetMapping("/test/account-ownership")
    fun trigger(): String {
        throw AccountOwnershipException(UUID.fromString("00000000-0000-0000-0000-000000000003"))
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class LedgerExceptionHandlerTest(@Autowired val mockMvc: MockMvc) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        test("handleAccountNotFound_accountNotFoundException_returns404WithErrorBody") {
            mockMvc.perform(get("/test/account-not-found"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Account not found"))
                .andExpect(jsonPath("$.details[0]").value("Account not found: 00000000-0000-0000-0000-000000000001"))
        }

        test("handleAccountNotActive_accountNotActiveException_returns403WithErrorBody") {
            mockMvc.perform(get("/test/account-not-active"))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Account not available"))
                .andExpect(jsonPath("$.details[0]").value("Account 00000000-0000-0000-0000-000000000002 is not available for this operation"))
        }

        test("handleAccountOwnership_accountOwnershipException_returns403WithErrorBody") {
            mockMvc.perform(get("/test/account-ownership"))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Account ownership violation"))
                .andExpect(jsonPath("$.details[0]").value("Account 00000000-0000-0000-0000-000000000003 does not belong to the requesting user"))
        }
    }
}
