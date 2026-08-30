package org.dpp.tradelab.config

import org.dpp.tradelab.user.controller.JwtAuthenticationFilter
import org.dpp.tradelab.user.controller.OidcAuthenticationSuccessHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val oidcAuthenticationSuccessHandler: OidcAuthenticationSuccessHandler,
    private val environment: Environment,
    @Value("\${app.cors.allowed-origin}")
    private val corsAllowedOrigin: String,
    @Value("\${app.frontend.origin}")
    private val frontendOrigin: String,
    @Value("\${app.features.enable-no-auth:true}")
    private val enableNoAuth: Boolean
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .headers { headers ->
                headers.frameOptions { it.disable() }   // needed for H2 console iframe
            }
            .authorizeHttpRequests { auth ->
                if (enableNoAuth) {
                    auth
                        // Registration — public when no-auth local testing is enabled
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                        // Legacy login and email list — public when no-auth local testing is enabled
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/emails").permitAll()
                }
                auth
                    // OAuth2 dance endpoints
                    .requestMatchers("/oauth2/authorization/**").permitAll()
                    .requestMatchers("/login/oauth2/code/**").permitAll()
                    // Infrastructure — H2 console, actuator, Spring Boot Admin
                    .requestMatchers("/h2-console/**").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/admin/**").permitAll()
                    // Test endpoints
                    .requestMatchers("/test/**").permitAll()
                    // In test mode, allow all requests
                    .apply {
                        if (environment.activeProfiles.contains("test")) {
                            anyRequest().permitAll()
                        } else {
                            // Everything else requires a valid internal JWT
                            anyRequest().authenticated()
                        }
                    }
            }
            // Only configure OAuth2 login in non-test mode
            .apply {
                if (!environment.activeProfiles.contains("test")) {
                    oauth2Login { oauth2 ->
                        oauth2.successHandler(oidcAuthenticationSuccessHandler)
                        oauth2.failureHandler { request, response, _ ->
                            val errorParam = if (request.requestURI.contains("github")) "github_oidc_failed" else "google_oidc_failed"
                            response.sendRedirect("$frontendOrigin/login?error=$errorParam")
                        }
                    }
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf(corsAllowedOrigin)
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
