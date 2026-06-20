package org.dpp.tradelab.user

import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserStatus
import org.dpp.tradelab.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class UserRepositoryTest @Autowired constructor(private val userRepository: UserRepository) {

    @Test
    fun existsByEmail_emailExists_returnsTrue() {
        userRepository.save(
            User(firstName = "Jane", lastName = "Doe", address = "123 Main St", email = "jane-repo@example.com")
        )
        assertTrue(userRepository.existsByEmail("jane-repo@example.com"))
    }

    @Test
    fun existsByEmail_emailDoesNotExist_returnsFalse() {
        assertFalse(userRepository.existsByEmail("nobody-repo@example.com"))
    }

    @Test
    fun findAllByStatus_activeUsersExist_returnsOnlyActiveUsers() {
        userRepository.save(User(firstName = "Alice", lastName = "A", address = "1 St", email = "alice@example.com", status = UserStatus.ACTIVE))
        userRepository.save(User(firstName = "Bob", lastName = "B", address = "2 St", email = "bob@example.com", status = UserStatus.SUSPENDED))
        userRepository.save(User(firstName = "Carol", lastName = "C", address = "3 St", email = "carol@example.com", status = UserStatus.ACTIVE))

        val results = userRepository.findAllByStatus(UserStatus.ACTIVE)

        val emails = results.map { it.email }
        assertTrue(emails.contains("alice@example.com"))
        assertTrue(emails.contains("carol@example.com"))
        assertFalse(emails.contains("bob@example.com"))
    }

    @Test
    fun findAllByStatus_noActiveUsers_returnsEmptyList() {
        userRepository.save(User(firstName = "Dave", lastName = "D", address = "4 St", email = "dave@example.com", status = UserStatus.CLOSED))

        val results = userRepository.findAllByStatus(UserStatus.ACTIVE)

        assertEquals(0, results.size)
    }
}
