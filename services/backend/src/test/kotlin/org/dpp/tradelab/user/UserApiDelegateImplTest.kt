package org.dpp.tradelab.user

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.dpp.tradelab.user.exception.DuplicateEmailException
import org.dpp.tradelab.user.service.UserService
import org.mockito.kotlin.any
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
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class UserApiDelegateImplTest(
    @Autowired val mockMvc: MockMvc,
    @MockitoBean val userService: UserService
) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    private val objectMapper = ObjectMapper()

    init {
        val validId = UUID.randomUUID()
        val validRequestBody = mapOf(
            "firstName" to "Jane",
            "lastName" to "Doe",
            "address" to "123 Main St",
            "email" to "jane@example.com"
        )

        test("registerUser_validRequest_returns201WithUserId") {
            whenever(userService.registerUser(any(), any(), any(), any())).thenReturn(validId)

            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequestBody))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.userId").value(validId.toString()))
        }

        test("registerUser_missingFirstName_returns400") {
            val body = validRequestBody.toMutableMap().apply { put("firstName", "") }

            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.status").value(400))
        }

        test("registerUser_invalidEmailFormat_returns400") {
            val body = validRequestBody.toMutableMap().apply { put("email", "not-an-email") }

            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.status").value(400))
        }

        test("registerUser_duplicateEmail_returns409") {
            whenever(userService.registerUser(any(), any(), any(), any()))
                .thenThrow(DuplicateEmailException("An account with this email already exists."))

            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequestBody))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.status").value(409))
        }
    }
}
