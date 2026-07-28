package org.dpp.tradelab.user.controller

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.dpp.tradelab.user.exception.InvalidTokenException
import org.dpp.tradelab.user.service.JwtService
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    // ObjectMapper is not injected as a Spring bean because this filter is instantiated
    // during security context creation, before Jackson auto-configuration has run in
    // some test slices. A private companion instance is safe — ObjectMapper is thread-safe.
    companion object {
        private val objectMapper = ObjectMapper()
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                val token = authHeader.substring("Bearer ".length)
                val userId = jwtService.validateAndExtractUserId(token)
                val authentication = UsernamePasswordAuthenticationToken(userId, null, emptyList())
                SecurityContextHolder.getContext().authentication = authentication
            }
            filterChain.doFilter(request, response)
        } catch (e: InvalidTokenException) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            val errorResponse = mapOf(
                "status" to HttpServletResponse.SC_UNAUTHORIZED,
                "error" to "Unauthorized",
                "details" to listOf("Invalid or expired token")
            )
            response.writer.write(objectMapper.writeValueAsString(errorResponse))
        }
    }
}
