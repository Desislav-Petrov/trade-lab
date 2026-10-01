package org.dpp.tradelab.user.api

import org.dpp.tradelab.user.service.JwtService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class TokenValidationApiImpl(
    private val jwtService: JwtService
) : TokenValidationApi {

    override fun validateAndExtractUserId(token: String): UUID =
        jwtService.validateAndExtractUserId(token)
}
