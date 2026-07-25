package org.dpp.tradelab.user.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
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
    private val signingKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    private val issuer = "trade-platform"
    private val expirationSeconds = 86400L // 24 hours

    fun issueToken(userId: UUID): String {
        val now = Date.from(Instant.now())
        val expiryDate = Date(now.time + expirationSeconds * 1000)

        return Jwts.builder()
            .subject(userId.toString())
            .issuer(issuer)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact()
    }

    fun validateAndExtractUserId(token: String): UUID {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseClaimsJws(token)
                .body

            UUID.fromString(claims.subject)
        } catch (e: Exception) {
            throw InvalidTokenException("Token validation failed: ${e.message}")
        }
    }
}
