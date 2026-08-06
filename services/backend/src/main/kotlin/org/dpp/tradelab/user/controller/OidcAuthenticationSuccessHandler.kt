package org.dpp.tradelab.user.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.dpp.tradelab.user.exception.OidcAuthenticationException
import org.dpp.tradelab.user.model.ProviderType
import org.dpp.tradelab.user.service.OidcAuthService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OidcAuthenticationSuccessHandler(
    private val oidcAuthService: OidcAuthService,
    @Value("\${app.frontend.origin}")
    private val frontendOrigin: String
) : AuthenticationSuccessHandler {

    private val logger = LoggerFactory.getLogger(OidcAuthenticationSuccessHandler::class.java)

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        try {
            val oidcUser = authentication.principal as OidcUser
            val subId: String = oidcUser.subject ?: throw IllegalArgumentException("Subject not provided by provider")
            val email: String = oidcUser.email ?: throw IllegalArgumentException("Email not provided by provider")
            val firstName: String = oidcUser.givenName ?: "User"
            val lastName: String = oidcUser.familyName ?: ""

            val jwt = oidcAuthService.handleCallback(
                providerType = ProviderType.GOOGLE,
                subId = subId,
                email = email,
                firstName = firstName,
                lastName = lastName
            )

            response.sendRedirect("$frontendOrigin/auth/callback?token=$jwt")
        } catch (e: OidcAuthenticationException) {
            logger.error("OIDC auth failed (OidcAuthenticationException)", e)
            response.sendRedirect("$frontendOrigin/login?error=server_error")
        } catch (e: Exception) {
            logger.error("OIDC auth failed (unexpected exception)", e)
            response.sendRedirect("$frontendOrigin/login?error=server_error")
        }
    }
}
