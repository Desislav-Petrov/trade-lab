package org.dpp.tradelab.user.repository

import org.dpp.tradelab.user.model.UserSettings
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserSettingsRepository : JpaRepository<UserSettings, UUID> {
    fun findByUserId(userId: UUID): UserSettings?
}
