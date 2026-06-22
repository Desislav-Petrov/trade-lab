package org.dpp.tradelab.user.api

import java.util.UUID

interface UserLookupApi {
    fun existsById(userId: UUID): Boolean
}
