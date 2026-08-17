package org.dpp.tradelab.user.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.dpp.tradelab.user.exception.OidcAuthenticationException
import org.dpp.tradelab.user.model.ProviderType
import org.dpp.tradelab.user.service.JwtService
import org.dpp.tradelab.user.service.OidcAuthService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OidcAuthenticationSuccessHandler(
    private val oidcAuthService: OidcAuthService,
    private val jwtService: JwtService,
    @Value("\${app.frontend.origin}")
    private val frontendOrigin: String
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val token = authentication as OAuth2AuthenticationToken
        try {
            when (token.authorizedClientRegistrationId) {
                "google" -> handleGoogle(token, response)
                "github" -> handleGithub(token, response)
                else -> response.sendRedirect("$frontendOrigin/login?error=server_error")
            }
        } catch (e: OidcAuthenticationException) {
            response.sendRedirect("$frontendOrigin/login?error=server_error")
        } catch (e: Exception) {
            response.sendRedirect("$frontendOrigin/login?error=server_error")
        }
    }

    private fun handleGoogle(token: OAuth2AuthenticationToken, response: HttpServletResponse) {
        val oidcUser = token.principal as OidcUser
        val subId = oidcUser.subject ?: throw IllegalArgumentException("Subject not provided by provider")
        val email = oidcUser.email ?: throw IllegalArgumentException("Email not provided by provider")
        val firstName = oidcUser.givenName ?: "User"
        val lastName = oidcUser.familyName ?: ""

        val jwt = oidcAuthService.handleCallback(ProviderType.GOOGLE, subId, email, firstName, lastName)
        response.sendRedirect("$frontendOrigin/auth/callback?token=$jwt")
    }

    private fun handleGithub(token: OAuth2AuthenticationToken, response: HttpServletResponse) {
        val oauth2User = token.principal as OAuth2User
        val attributes = oauth2User.attributes
        val subId = attributes["id"].toString()
        val email = attributes["email"] as? String

        if (email.isNullOrBlank()) {
            response.sendRedirect("$frontendOrigin/login?error=github_no_email")
            return
        }

        val name = attributes["name"] as? String
        val (firstName, lastName) = splitName(name)

        val jwt = oidcAuthService.handleCallback(ProviderType.GITHUB, subId, email, firstName, lastName)
        response.sendRedirect("$frontendOrigin/auth/callback?token=$jwt")
    }

    private fun splitName(name: String?): Pair<String, String> {
        if (name.isNullOrBlank()) return Pair("User", "")
        val spaceIndex = name.indexOf(' ')
        return if (spaceIndex >= 0) {
            Pair(name.substring(0, spaceIndex), name.substring(spaceIndex + 1))
        } else {
            Pair(name, "")
        }
    }
}
