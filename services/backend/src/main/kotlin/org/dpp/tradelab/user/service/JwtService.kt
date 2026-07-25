package org.dpp.tradelab.user.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.dpp.tradelab.user.exception.InvalidTokenException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID

@Service
class JwtService(
    @Value("\${app.jwt.secret}")
    private val jwtSecret: String
) {
    private val signingKey by lazy { Keys.hmacShaKeyFor(jwtSecret.toByteArray()) }
    private val issuer = "trade-platform"
    private val expirationSeconds = 86400L

    fun issueToken(userId: UUID): String {
        val now = Instant.now()
        val expiry = now.plusSeconds(expirationSeconds)

        return Jwts.builder()
            .subject(userId.toString())
            .issuer(issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey)
            .compact()
    }

    fun validateAndExtractUserId(token: String): UUID {
        return try {
            val claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .payload

            UUID.fromString(claims.subject)
        } catch (e: Exception) {
            throw InvalidTokenException("Token validation failed: ${e.message}")
        }
    }
}
