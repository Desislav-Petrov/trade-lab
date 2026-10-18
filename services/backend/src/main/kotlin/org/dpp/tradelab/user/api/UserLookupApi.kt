package org.dpp.tradelab.user.api

import java.util.UUID

interface UserLookupApi {
    fun existsById(userId: UUID): Boolean

    /**
     * Published cross-domain contract for resolving a user's full name.
     *
     * @return the user's full name, or null when the user does not exist
     */
    fun getName(userId: UUID): String?
}
