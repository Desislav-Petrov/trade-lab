package org.dpp.tradelab.user.controller

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.dpp.tradelab.user.exception.InvalidTokenException
import org.dpp.tradelab.user.service.JwtService
import org.slf4j.LoggerFactory
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

    companion object {
        private val objectMapper = ObjectMapper()
        private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)

        // DEBUG: log exactly what the backend receives so we can tell whether
        // the Vite proxy is forwarding the Authorization header.
        if (authHeader == null) {
            log.warn("[JwtFilter] {} {} — Authorization header ABSENT",
                request.method, request.requestURI)
        } else if (authHeader.startsWith("Bearer ")) {
            val prefix = authHeader.substring(7).take(20)
            log.info("[JwtFilter] {} {} — Authorization header present, token prefix: {}…",
                request.method, request.requestURI, prefix)
        } else {
            log.warn("[JwtFilter] {} {} — Authorization header present but unexpected format: {}",
                request.method, request.requestURI, authHeader.take(30))
        }

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                val token = authHeader.substring("Bearer ".length)
                val userId = jwtService.validateAndExtractUserId(token)
                log.info("[JwtFilter] token valid — authenticated as userId={}", userId)
                val authentication = UsernamePasswordAuthenticationToken(userId, null, emptyList())
                SecurityContextHolder.getContext().authentication = authentication
            }
            filterChain.doFilter(request, response)
        } catch (e: InvalidTokenException) {
            log.warn("[JwtFilter] InvalidTokenException: {}", e.message)
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            val errorResponse = mapOf(
                "status" to HttpServletResponse.SC_UNAUTHORIZED,
                "error" to "Unauthorized",
                "details" to listOf("Invalid or expired token")
            )
            response.writer.write(objectMapper.writeValueAsString(errorResponse))
        } catch (e: Exception) {
            // Catch-all: any unexpected exception during token validation should
            // not silently swallow — log it so we can see it in the terminal.
            log.error("[JwtFilter] Unexpected exception during token validation: {}", e.message, e)
            filterChain.doFilter(request, response)
        }
    }
}
