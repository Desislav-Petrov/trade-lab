package org.dpp.tradelab.user

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.user.controller.UserNoAuthApiDelegateImpl
import org.dpp.tradelab.user.service.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "app.features.enable-no-auth=false"
    ]
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserNoAuthDisabledTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val applicationContext: ApplicationContext,
    @MockitoBean val userService: UserService
) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    private val objectMapper = ObjectMapper()

    init {
        test("userNoAuthApiDelegateImpl_disabledProperty_doesNotRegisterBean") {
            applicationContext.getBeansOfType(UserNoAuthApiDelegateImpl::class.java).isEmpty() shouldBe true
        }

        test("registerUser_disabledProperty_returns404") {
            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "firstName" to "Jane",
                                "lastName" to "Doe",
                                "address" to "123 Main St",
                                "email" to "jane@example.com"
                            )
                        )
                    )
            )
                .andExpect(status().isNotFound)
        }

        test("loginUser_disabledProperty_returns404") {
            mockMvc.perform(
                post("/api/v1/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("email" to "jane@example.com")))
            )
                .andExpect(status().isNotFound)
        }

        test("getActiveUserEmails_disabledProperty_returns404") {
            mockMvc.perform(get("/api/v1/users/emails"))
                .andExpect(status().isNotFound)
        }
    }
}
