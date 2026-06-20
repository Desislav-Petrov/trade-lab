package org.dpp.tradelab.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.user.exception.DuplicateEmailException
import org.dpp.tradelab.user.messaging.UserRegisteredEvent
import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserStatus
import org.dpp.tradelab.user.repository.UserRepository
import org.dpp.tradelab.user.service.UserService
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class UserServiceTest : FunSpec({

    val userRepository = mock<UserRepository>()
    val eventPublisher = mock<ApplicationEventPublisher>()
    val userService = UserService(userRepository, eventPublisher)
    val validId = UUID.randomUUID()

    beforeEach {
        reset(userRepository, eventPublisher)
    }

    test("registerUser_duplicateEmail_throwsDuplicateEmailException") {
        whenever(userRepository.existsByEmail("dupe@example.com")).thenReturn(true)

        shouldThrow<DuplicateEmailException> {
            userService.registerUser("Jane", "Doe", "123 Main St", "dupe@example.com")
        }

        verify(userRepository, never()).save(any())
    }

    test("registerUser_validInput_persistsUserWithActiveStatus") {
        whenever(userRepository.existsByEmail("jane@example.com")).thenReturn(false)
        val savedUser = User(id = validId, firstName = "Jane", lastName = "Doe", address = "123 Main St", email = "jane@example.com", status = UserStatus.ACTIVE)
        whenever(userRepository.save(any())).thenReturn(savedUser)

        userService.registerUser("Jane", "Doe", "123 Main St", "jane@example.com")

        val captor = argumentCaptor<User>()
        verify(userRepository).save(captor.capture())
        captor.firstValue.status shouldBe UserStatus.ACTIVE
        captor.firstValue.email shouldBe "jane@example.com"
    }

    test("registerUser_validInput_returnsNewUserId") {
        whenever(userRepository.existsByEmail("jane@example.com")).thenReturn(false)
        val savedUser = User(id = validId, firstName = "Jane", lastName = "Doe", address = "123 Main St", email = "jane@example.com")
        whenever(userRepository.save(any())).thenReturn(savedUser)

        val result = userService.registerUser("Jane", "Doe", "123 Main St", "jane@example.com")

        result shouldBe validId
    }

    test("registerUser_validInput_publishesUserRegisteredEvent") {
        whenever(userRepository.existsByEmail("jane@example.com")).thenReturn(false)
        val savedUser = User(id = validId, firstName = "Jane", lastName = "Doe", address = "123 Main St", email = "jane@example.com")
        whenever(userRepository.save(any())).thenReturn(savedUser)

        userService.registerUser("Jane", "Doe", "123 Main St", "jane@example.com")

        val captor = argumentCaptor<UserRegisteredEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        captor.firstValue.userId shouldBe validId
        captor.firstValue.email shouldBe "jane@example.com"
        captor.firstValue.timestamp shouldNotBe null
    }

    test("getActiveUserEmails_activeUsersExist_returnsEmailList") {
        val activeUsers = listOf(
            User(id = UUID.randomUUID(), firstName = "Alice", lastName = "A", address = "1 St", email = "alice@example.com", status = UserStatus.ACTIVE),
            User(id = UUID.randomUUID(), firstName = "Bob", lastName = "B", address = "2 St", email = "bob@example.com", status = UserStatus.ACTIVE)
        )
        whenever(userRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(activeUsers)

        val result = userService.getActiveUserEmails()

        result shouldContainExactlyInAnyOrder listOf("alice@example.com", "bob@example.com")
    }

    test("getActiveUserEmails_noActiveUsers_returnsEmptyList") {
        whenever(userRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(emptyList())

        val result = userService.getActiveUserEmails()

        result shouldBe emptyList()
    }
})
