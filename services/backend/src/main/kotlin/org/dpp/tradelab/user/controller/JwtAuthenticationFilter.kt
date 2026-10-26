package org.dpp.tradelab.user.controller

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.dpp.tradelab.common.exception.InvalidTokenException
import org.dpp.tradelab.common.service.JwtService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

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

        // Only token validation is guarded. On an invalid token we write a 401 and
        // return without continuing the chain. Every other path falls through to the
        // single filterChain.doFilter call below, so the chain is invoked exactly once
        // per request and never from inside this try/catch.
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                val token = authHeader.substring("Bearer ".length)
                val userId = jwtService.validateAndExtractUserId(token)
                if (log.isDebugEnabled) {
                    log.debug("[JwtFilter] {} {} — authenticated userId={}",
                        request.method, request.requestURI, userId)
                }
                val authentication = UsernamePasswordAuthenticationToken(userId, null, emptyList())
                SecurityContextHolder.getContext().authentication = authentication
            }
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
            return
        }

        filterChain.doFilter(request, response)
    }
}
