package org.dpp.tradelab.user.repository

import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun existsByEmail(email: String): Boolean
    fun findByEmail(email: String): Optional<User>

    @Query("select u.email from User u where u.status = :status")
    fun findEmailsByStatus(@Param("status") status: UserStatus): List<String>
}
