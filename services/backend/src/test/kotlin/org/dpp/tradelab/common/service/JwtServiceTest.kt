package org.dpp.tradelab.common.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.common.exception.InvalidTokenException
import java.time.Instant
import java.util.Date
import java.util.UUID

class JwtServiceTest : FunSpec({
    val secret = "a".repeat(32)
    val jwtService = JwtService(secret)

    test("validateAndExtractUserId_validToken_returnsSubject") {
        val userId = UUID.randomUUID()
        val token = jwtService.issueToken(userId)

        jwtService.validateAndExtractUserId(token) shouldBe userId
    }

    test("validateAndExtractUserId_malformedToken_throwsInvalidTokenException") {
        shouldThrow<InvalidTokenException> {
            jwtService.validateAndExtractUserId("not-a-jwt")
        }
    }

    test("validateAndExtractUserId_expiredToken_throwsInvalidTokenException") {
        val now = Instant.now()
        val token = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuer("trade-platform")
            .issuedAt(Date.from(now.minusSeconds(120)))
            .expiration(Date.from(now.minusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(secret.toByteArray()))
            .compact()

        shouldThrow<InvalidTokenException> {
            jwtService.validateAndExtractUserId(token)
        }
    }
})
