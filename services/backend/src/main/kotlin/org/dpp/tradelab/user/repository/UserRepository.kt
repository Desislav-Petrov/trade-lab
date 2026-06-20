package org.dpp.tradelab.user.repository

import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun existsByEmail(email: String): Boolean
    fun findByEmailAndStatus(email: String, status: UserStatus): User?
}
