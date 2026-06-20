package org.dpp.tradelab.user

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.dpp.tradelab.user.model.UserStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Entity
class UserStatusTestEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Enumerated(EnumType.STRING)
    val status: UserStatus = UserStatus.ACTIVE
)

@SpringBootTest
@AutoConfigureTestEntityManager
@Transactional
class UserStatusTest @Autowired constructor(private val em: TestEntityManager) {

    @Test
    fun userStatus_persistAndRead_storedAsString() {
        val entity = UserStatusTestEntity(status = UserStatus.ACTIVE)
        val saved = em.persistAndFlush(entity)
        em.clear()

        val found = em.find(UserStatusTestEntity::class.java, saved.id!!)
        assertNotNull(found)
        assertEquals(UserStatus.ACTIVE, found!!.status)
    }

    @Test
    fun userStatus_allValues_areCorrect() {
        val values = UserStatus.entries
        assertEquals(3, values.size)
        assert(values.contains(UserStatus.ACTIVE))
        assert(values.contains(UserStatus.SUSPENDED))
        assert(values.contains(UserStatus.CLOSED))
    }
}
