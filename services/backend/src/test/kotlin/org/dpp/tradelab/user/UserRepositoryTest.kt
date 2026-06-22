package org.dpp.tradelab.user

import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class UserRepositoryTest @Autowired constructor(private val userRepository: UserRepository) {

    @Test
    fun existsByEmail_emailExists_returnsTrue() {
        userRepository.save(
            User(id = UUID.randomUUID(), firstName = "Jane", lastName = "Doe", address = "123 Main St", email = "jane-repo@example.com")
        )
        assertTrue(userRepository.existsByEmail("jane-repo@example.com"))
    }

    @Test
    fun existsByEmail_emailDoesNotExist_returnsFalse() {
        assertFalse(userRepository.existsByEmail("nobody-repo@example.com"))
    }
}
