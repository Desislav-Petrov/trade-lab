package org.dpp.tradelab.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import org.dpp.tradelab.user.controller.JwtAuthenticationFilter
import org.dpp.tradelab.user.controller.OidcAuthenticationSuccessHandler
import org.mockito.kotlin.mock
import org.springframework.core.env.Environment
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Unit tests for the CORS wiring in [SecurityConfig]. The REST CORS policy must
 * honour the full list of configured origins (not just the first), so that the
 * frontend works from both `localhost` and `127.0.0.1`.
 */
class SecurityConfigCorsTest : FunSpec({

    fun buildConfig(origins: List<String>): SecurityConfig =
        SecurityConfig(
            jwtAuthenticationFilter = mock<JwtAuthenticationFilter>(),
            oidcAuthenticationSuccessHandler = mock<OidcAuthenticationSuccessHandler>(),
            environment = mock<Environment>(),
            corsAllowedOrigins = origins,
            frontendOrigin = "http://localhost:5173",
        )

    test("corsConfigurationSource_multipleOrigins_allowsEveryConfiguredOrigin") {
        val origins = listOf("http://localhost:5173", "http://127.0.0.1:5173")

        val source = buildConfig(origins).corsConfigurationSource() as UrlBasedCorsConfigurationSource
        val config = source.corsConfigurations["/**"]

        config.shouldNotBeNull()
        config.allowedOrigins shouldBe origins
        config.allowCredentials shouldBe true
    }

    test("corsConfigurationSource_singleOrigin_allowsThatOrigin") {
        val origins = listOf("https://app.example.com")

        val source = buildConfig(origins).corsConfigurationSource() as UrlBasedCorsConfigurationSource
        val config = source.corsConfigurations["/**"]

        config.shouldNotBeNull()
        config.allowedOrigins shouldBe origins
    }
})
