package org.dpp.tradelab.user

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import org.dpp.tradelab.user.controller.NoAuthUserApiDelegateImpl
import org.dpp.tradelab.user.model.FeedType
import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserSettings
import org.dpp.tradelab.user.model.UserStatus
import org.dpp.tradelab.user.service.UserService
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest(
    properties = [
        "app.features.enable-no-auth=false",
        "spring.profiles.active=test"
    ]
)
@AutoConfigureMockMvc
class NoAuthUserApiDelegateImplDisabledTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val applicationContext: ApplicationContext,
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

        fun makeUserWithSettings(id: UUID = validId): User {
            val user = User(
                id = id,
                firstName = "Jane",
                lastName = "Doe",
                address = "123 Main St",
                email = "jane@example.com",
                status = UserStatus.ACTIVE,
                createdAt = Instant.parse("2026-01-01T00:00:00Z")
            )
            user.settings = UserSettings(
                id = UUID.randomUUID(),
                userId = id,
                feedType = FeedType.SYNTHETIC
            ).also { it.updatedAt = Instant.parse("2026-01-01T00:00:00Z") }
            return user
        }

        test("noAuthUserApiDelegate_enableNoAuthFalse_doesNotRegisterBean") {
            applicationContext.getBeanNamesForType(NoAuthUserApiDelegateImpl::class.java).toList().shouldBeEmpty()
        }

        test("securityConfig_enableNoAuthFalse_loadsFilterChain") {
            applicationContext.getBeanNamesForType(SecurityFilterChain::class.java).toList() shouldHaveSize 1
        }

        test("registerUser_enableNoAuthFalse_returns404") {
            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequestBody))
            )
                .andExpect(status().isNotFound)
        }

        test("loginUser_enableNoAuthFalse_returns404") {
            mockMvc.perform(
                post("/api/v1/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("email" to "jane@example.com")))
            )
                .andExpect(status().isNotFound)
        }

        test("getActiveUserEmails_enableNoAuthFalse_returns404") {
            mockMvc.perform(get("/api/v1/users/emails"))
                .andExpect(status().isNotFound)
        }

        test("getUserById_enableNoAuthFalse_returns200") {
            val user = makeUserWithSettings(validId)
            whenever(userService.getUserById(validId)).thenReturn(user)

            mockMvc.perform(get("/api/v1/users/$validId"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("\$.userId").value(validId.toString()))
        }

        test("updateUserSettings_enableNoAuthFalse_returns200") {
            val settings = UserSettings(
                id = UUID.randomUUID(),
                userId = validId,
                feedType = FeedType.REAL
            ).also { it.updatedAt = Instant.parse("2026-01-01T00:00:00Z") }
            whenever(userService.updateUserSettings(eq(validId), any())).thenReturn(settings)

            mockMvc.perform(
                patch("/api/v1/users/$validId/settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"feedType": "REAL"}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("\$.feedType").value("REAL"))
        }
    }
}
