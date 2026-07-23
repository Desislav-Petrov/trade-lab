package org.dpp.tradelab.user.repository

import org.dpp.tradelab.user.model.UserSettings
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserSettingsRepository : JpaRepository<UserSettings, UUID> {
    fun findByUserId(userId: UUID): UserSettings?
    fun findAllBy(): List<UserSettings>
}
