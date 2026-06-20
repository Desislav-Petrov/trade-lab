package org.dpp.tradelab.user

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.dpp.tradelab.user.exception.DuplicateEmailException
import org.dpp.tradelab.user.exception.UserNotFoundException
import org.dpp.tradelab.user.exception.UserNotActiveException
import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserStatus
import org.dpp.tradelab.user.service.UserService
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
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

        test("getActiveUserEmails_activeUsersExist_returns200WithEmailList") {
            whenever(userService.getActiveUserEmails())
                .thenReturn(listOf("alice@example.com", "bob@example.com"))

            mockMvc.perform(get("/api/v1/users/emails"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.emails[0]").value("alice@example.com"))
                .andExpect(jsonPath("$.emails[1]").value("bob@example.com"))
        }

        test("getActiveUserEmails_noActiveUsers_returns200WithEmptyList") {
            whenever(userService.getActiveUserEmails()).thenReturn(emptyList())

            mockMvc.perform(get("/api/v1/users/emails"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.emails").isArray)
                .andExpect(jsonPath("$.emails").isEmpty)
        }

        test("getUserById_existingUser_returns200WithUserResponse") {
            val user = User(
                id = validId,
                firstName = "Jane",
                lastName = "Doe",
                address = "123 Main St",
                email = "jane@example.com",
                status = UserStatus.ACTIVE,
                createdAt = Instant.parse("2026-01-01T00:00:00Z")
            )
            whenever(userService.getUserById(validId)).thenReturn(user)

            mockMvc.perform(get("/api/v1/users/$validId"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.userId").value(validId.toString()))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.status").value("active"))
        }

        test("getUserById_unknownId_returns404") {
            whenever(userService.getUserById(validId))
                .thenThrow(UserNotFoundException(validId))

            mockMvc.perform(get("/api/v1/users/$validId"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.status").value(404))
        }

        test("loginUser_activeUser_returns200WithLoginResponse") {
            val user = User(
                id = validId,
                firstName = "Jane",
                lastName = "Doe",
                address = "123 Main St",
                email = "jane@example.com",
                status = UserStatus.ACTIVE
            )
            whenever(userService.loginUser("jane@example.com")).thenReturn(user)

            mockMvc.perform(
                post("/api/v1/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("email" to "jane@example.com")))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.userId").value(validId.toString()))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
        }

        test("loginUser_unknownEmail_returns404") {
            whenever(userService.loginUser("ghost@example.com"))
                .thenThrow(UserNotFoundException(UUID.randomUUID()))

            mockMvc.perform(
                post("/api/v1/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("email" to "ghost@example.com")))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.status").value(404))
        }

        test("loginUser_suspendedUser_returns403") {
            whenever(userService.loginUser("sus@example.com"))
                .thenThrow(UserNotActiveException("sus@example.com"))

            mockMvc.perform(
                post("/api/v1/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("email" to "sus@example.com")))
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
        }
    }
}
