package org.dpp.tradelab.user.controller

import io.kotest.core.spec.style.FunSpec
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.dpp.tradelab.user.exception.OidcAuthenticationException
import org.dpp.tradelab.user.model.ProviderType
import org.dpp.tradelab.user.service.JwtService
import org.dpp.tradelab.user.service.OidcAuthService
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import java.time.Instant

class OidcAuthenticationSuccessHandlerTest : FunSpec() {

    private val oidcAuthService = mock<OidcAuthService>()
    private val jwtService = mock<JwtService>()
    private val frontendOrigin = "http://localhost:5173"
    private val request = mock<HttpServletRequest>()
    private val response = mock<HttpServletResponse>()

    private val handler = OidcAuthenticationSuccessHandler(oidcAuthService, jwtService, frontendOrigin)

    private fun googleToken(subject: String, email: String, givenName: String?, familyName: String?): OAuth2AuthenticationToken {
        val claims = mutableMapOf<String, Any>(
            "sub" to subject,
            "email" to email,
        )
        if (givenName != null) claims["given_name"] = givenName
        if (familyName != null) claims["family_name"] = familyName
        val idToken = OidcIdToken("id-token-value", Instant.now(), Instant.now().plusSeconds(3600), claims)
        val authority = OidcUserAuthority(idToken)
        val oidcUser = DefaultOidcUser(listOf(authority), idToken, "sub")
        return OAuth2AuthenticationToken(oidcUser, oidcUser.authorities, "google")
    }

    private fun githubToken(id: Int, email: String?, name: String?): OAuth2AuthenticationToken {
        val attrs = mutableMapOf<String, Any>("id" to id)
        if (email != null) attrs["email"] = email
        if (name != null) attrs["name"] = name
        val oauth2User = DefaultOAuth2User(emptyList(), attrs, "id")
        return OAuth2AuthenticationToken(oauth2User, oauth2User.authorities, "github")
    }

    init {
        beforeTest {
            reset(oidcAuthService, jwtService, request, response)
        }

        test("Google happy path - redirects to auth callback with jwt") {
            val authToken = googleToken("google-sub-1", "user@example.com", "John", "Doe")
            whenever(oidcAuthService.handleCallback(ProviderType.GOOGLE, "google-sub-1", "user@example.com", "John", "Doe"))
                .thenReturn("test-jwt")

            handler.onAuthenticationSuccess(request, response, authToken)

            verify(response).sendRedirect("$frontendOrigin/auth/callback?token=test-jwt")
        }

        test("GitHub happy path - two-token name - redirects to auth callback with jwt") {
            val authToken = githubToken(42, "user@github.com", "Jane Doe")
            whenever(oidcAuthService.handleCallback(ProviderType.GITHUB, "42", "user@github.com", "Jane", "Doe"))
                .thenReturn("github-jwt")

            handler.onAuthenticationSuccess(request, response, authToken)

            verify(response).sendRedirect("$frontendOrigin/auth/callback?token=github-jwt")
        }

        test("GitHub happy path - single-token name") {
            val authToken = githubToken(99, "solo@github.com", "Solo")
            whenever(oidcAuthService.handleCallback(ProviderType.GITHUB, "99", "solo@github.com", "Solo", ""))
                .thenReturn("jwt-solo")

            handler.onAuthenticationSuccess(request, response, authToken)

            verify(response).sendRedirect("$frontendOrigin/auth/callback?token=jwt-solo")
        }

        test("GitHub happy path - multi-token name - splits on first space only") {
            val authToken = githubToken(77, "multi@github.com", "Alice Bob Charlie")
            whenever(oidcAuthService.handleCallback(ProviderType.GITHUB, "77", "multi@github.com", "Alice", "Bob Charlie"))
                .thenReturn("jwt-multi")

            handler.onAuthenticationSuccess(request, response, authToken)

            verify(response).sendRedirect("$frontendOrigin/auth/callback?token=jwt-multi")
        }

        test("GitHub happy path - null name - firstName=User lastName=empty") {
            val authToken = githubToken(55, "noname@github.com", null)
            whenever(oidcAuthService.handleCallback(ProviderType.GITHUB, "55", "noname@github.com", "User", ""))
                .thenReturn("jwt-noname")

            handler.onAuthenticationSuccess(request, response, authToken)

            verify(response).sendRedirect("$frontendOrigin/auth/callback?token=jwt-noname")
        }

        test("GitHub null email - redirects to github_no_email - service NOT called") {
            val authToken = githubToken(11, null, "Some User")

            handler.onAuthenticationSuccess(request, response, authToken)

            verify(response).sendRedirect("$frontendOrigin/login?error=github_no_email")
            verify(oidcAuthService, never()).handleCallback(any(), any(), any(), any(), any())
        }

        test("GitHub blank email - redirects to github_no_email - service NOT called") {
            val authToken = githubToken(12, "   ", "Some User")

            handler.onAuthenticationSuccess(request, response, authToken)

            verify(response).sendRedirect("$frontendOrigin/login?error=github_no_email")
            verify(oidcAuthService, never()).handleCallback(any(), any(), any(), any(), any())
        }

        test("OidcAuthenticationException thrown - redirects to server_error") {
            val authToken = googleToken("sub-err", "err@example.com", "Err", "User")
            whenever(oidcAuthService.handleCallback(any(), any(), any(), any(), any()))
                .thenThrow(OidcAuthenticationException("oidc error", RuntimeException()))

            handler.onAuthenticationSuccess(request, response, authToken)

            verify(response).sendRedirect("$frontendOrigin/login?error=server_error")
        }

        test("Unexpected exception thrown - redirects to server_error") {
            val authToken = googleToken("sub-unexpected", "unexpected@example.com", "Foo", "Bar")
            whenever(oidcAuthService.handleCallback(any(), any(), any(), any(), any()))
                .thenThrow(RuntimeException("unexpected"))

            handler.onAuthenticationSuccess(request, response, authToken)

            verify(response).sendRedirect("$frontendOrigin/login?error=server_error")
        }
    }
}
