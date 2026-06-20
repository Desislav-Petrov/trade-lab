package org.dpp.tradelab.user

import jakarta.persistence.PersistenceException
import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureTestEntityManager
@Transactional
class UserEntityTest @Autowired constructor(private val em: TestEntityManager) {

    @Test
    fun persistUser_validFields_idAndTimestampsAreGenerated() {
        val user = User(
            firstName = "Jane",
            lastName = "Doe",
            address = "123 Main St",
            email = "jane.doe@example.com"
        )
        val saved = em.persistAndFlush(user)
        em.clear()

        val found = em.find(User::class.java, saved.id!!)
        assertNotNull(found)
        found!!
        assertNotNull(found.id)
        assertEquals("Jane", found.firstName)
        assertEquals("Doe", found.lastName)
        assertEquals("123 Main St", found.address)
        assertEquals("jane.doe@example.com", found.email)
        assertEquals(UserStatus.ACTIVE, found.status)
        assertNotNull(found.createdAt)
        assertNotNull(found.updatedAt)
    }

    @Test
    fun persistUser_duplicateEmail_throwsConstraintViolation() {
        val user1 = User(firstName = "Jane", lastName = "Doe", address = "123 Main St", email = "dupe-entity@example.com")
        em.persistAndFlush(user1)

        assertThrows<PersistenceException> {
            val user2 = User(firstName = "John", lastName = "Smith", address = "456 Oak Ave", email = "dupe-entity@example.com")
            em.persistAndFlush(user2)
        }
    }

    @Test
    fun persistUser_statusDefaultsToActive() {
        val user = User(firstName = "Alice", lastName = "Smith", address = "1 Road", email = "alice-entity@example.com")
        val saved = em.persistAndFlush(user)
        em.clear()

        val found = em.find(User::class.java, saved.id!!)
        assertNotNull(found)
        assertEquals(UserStatus.ACTIVE, found!!.status)
    }
}
