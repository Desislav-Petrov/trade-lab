package org.dpp.tradelab.user.api

import java.util.UUID

interface TokenValidationApi {
    fun validateAndExtractUserId(token: String): UUID
}
