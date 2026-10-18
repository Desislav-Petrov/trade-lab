package org.dpp.tradelab.agent.service

import org.dpp.tradelab.config.AGENT_APP_NAME
import org.dpp.tradelab.user.api.UserLookupApi
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AgentUserProfileService(
    private val persistentSessionService: PersistentSessionService,
    private val userLookupApi: UserLookupApi
) {
    fun resolveName(userId: UUID): String? {
        val stateUserId = userId.toString()

        val cachedName = readCachedName(stateUserId)
        if (!cachedName.isNullOrBlank()) {
            return cachedName
        }

        val name = resolveNameFromUser(userId)
        if (name.isNullOrBlank()) {
            return null
        }

        cacheName(stateUserId, name)
        return name
    }

    private fun resolveNameFromUser(userId: UUID): String? =
        try {
            userLookupApi.getName(userId)
        } catch (_: Exception) {
            null
        }

    private fun readCachedName(stateUserId: String): String? =
        try {
            persistentSessionService.getUserScopedValue(
                AGENT_APP_NAME,
                stateUserId,
                USER_PROFILE_KEY
            ) as? String
        } catch (_: Exception) {
            null
        }

    private fun cacheName(stateUserId: String, name: String) {
        try {
            persistentSessionService.updateUserScopedValue(
                AGENT_APP_NAME,
                stateUserId,
                USER_PROFILE_KEY,
                name
            )
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val USER_PROFILE_KEY = "user:user_profile"
    }
}
