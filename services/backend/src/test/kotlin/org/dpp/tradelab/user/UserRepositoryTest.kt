package org.dpp.tradelab.user

import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserStatus
import org.dpp.tradelab.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
    fun findByEmailAndStatus_activeUserWithMatchingEmail_returnsUser() {
        userRepository.save(
            User(firstName = "Alice", lastName = "A", address = "1 St", email = "alice@example.com", status = UserStatus.ACTIVE)
        )

        val result = userRepository.findByEmailAndStatus("alice@example.com", UserStatus.ACTIVE)

        assertNotNull(result)
        assertTrue(result!!.email == "alice@example.com")
        assertTrue(result.status == UserStatus.ACTIVE)
    }

    @Test
    fun findByEmailAndStatus_emailExistsButStatusMismatch_returnsNull() {
        userRepository.save(
            User(firstName = "Bob", lastName = "B", address = "2 St", email = "bob@example.com", status = UserStatus.SUSPENDED)
        )

        val result = userRepository.findByEmailAndStatus("bob@example.com", UserStatus.ACTIVE)

        assertNull(result)
    }

    @Test
    fun findByEmailAndStatus_emailDoesNotExist_returnsNull() {
        val result = userRepository.findByEmailAndStatus("nobody@example.com", UserStatus.ACTIVE)

        assertNull(result)
    }
}
