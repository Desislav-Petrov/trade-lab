package org.dpp.tradelab

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.dpp.tradelab.user.api.DuplicateEmailException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DuplicateEmailTestController {
    @GetMapping("/test/duplicate-email")
    fun trigger(): String {
        throw DuplicateEmailException("An account with this email already exists.")
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest(@Autowired val mockMvc: MockMvc) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        test("handleDuplicateEmail_duplicateEmailException_returns409WithErrorBody") {
            mockMvc.perform(get("/test/duplicate-email"))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Email already registered"))
                .andExpect(jsonPath("$.details[0]").value("An account with this email already exists."))
        }
    }
}
