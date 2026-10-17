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
        return try {
            val stateUserId = userId.toString()
            val cachedName = persistentSessionService.getUserScopedValue(
                AGENT_APP_NAME,
                stateUserId,
                USER_PROFILE_KEY
            ) as? String
            if (!cachedName.isNullOrBlank()) {
                cachedName
            } else {
                val name = userLookupApi.getName(userId)
                if (name.isNullOrBlank()) {
                    null
                } else {
                    persistentSessionService.updateUserScopedValue(
                        AGENT_APP_NAME,
                        stateUserId,
                        USER_PROFILE_KEY,
                        name
                    )
                    name
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val USER_PROFILE_KEY = "user:user_profile"
    }
}
