package org.dpp.tradelab.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.user.exception.DuplicateEmailException
import org.dpp.tradelab.user.exception.UserNotFoundException
import org.dpp.tradelab.user.exception.UserNotActiveException
import org.dpp.tradelab.user.exception.UserSettingsNotFoundException
import org.dpp.tradelab.user.messaging.UserRegisteredEvent
import org.dpp.tradelab.user.messaging.UserSettingsChangedEvent
import org.dpp.tradelab.user.model.FeedType
import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserSettings
import org.dpp.tradelab.user.model.UserStatus
import org.dpp.tradelab.user.repository.UserRepository
import org.dpp.tradelab.user.repository.UserSettingsRepository
import org.dpp.tradelab.user.service.UserService
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.Optional
import java.util.UUID

class UserServiceTest : FunSpec({

    val userRepository = mock<UserRepository>()
    val userSettingsRepository = mock<UserSettingsRepository>()
    val eventPublisher = mock<ApplicationEventPublisher>()
    val userService = UserService(userRepository, userSettingsRepository, eventPublisher)
    val validId = UUID.randomUUID()

    fun makeUser(id: UUID = validId, status: UserStatus = UserStatus.ACTIVE) =
        User(id = id, firstName = "Jane", lastName = "Doe", address = "123 Main St", email = "jane@example.com", status = status)

    fun makeSettings(userId: UUID = validId, feedType: FeedType = FeedType.SYNTHETIC) =
        UserSettings(id = UUID.randomUUID(), userId = userId, feedType = feedType).also {
            it.updatedAt = Instant.now()
        }

    beforeEach {
        reset(userRepository, userSettingsRepository, eventPublisher)
    }

    test("registerUser_duplicateEmail_throwsDuplicateEmailException") {
        whenever(userRepository.existsByEmail("dupe@example.com")).thenReturn(true)

        shouldThrow<DuplicateEmailException> {
            userService.registerUser("Jane", "Doe", "123 Main St", "dupe@example.com")
        }

        verify(userRepository, never()).save(any())
        verify(userSettingsRepository, never()).save(any())
    }

    test("registerUser_validInput_persistsUserWithActiveStatus") {
        whenever(userRepository.existsByEmail("jane@example.com")).thenReturn(false)
        val savedUser = makeUser()
        whenever(userRepository.save(any())).thenReturn(savedUser)
        whenever(userSettingsRepository.save(any())).thenAnswer { it.getArgument<UserSettings>(0) }

        userService.registerUser("Jane", "Doe", "123 Main St", "jane@example.com")

        val captor = argumentCaptor<User>()
        verify(userRepository).save(captor.capture())
        captor.firstValue.status shouldBe UserStatus.ACTIVE
        captor.firstValue.email shouldBe "jane@example.com"
    }

    test("registerUser_validInput_returnsNewUserId") {
        whenever(userRepository.existsByEmail("jane@example.com")).thenReturn(false)
        whenever(userRepository.save(any())).thenAnswer { it.getArgument<User>(0) }
        whenever(userSettingsRepository.save(any())).thenAnswer { it.getArgument<UserSettings>(0) }

        val result = userService.registerUser("Jane", "Doe", "123 Main St", "jane@example.com")

        val captor = argumentCaptor<User>()
        verify(userRepository).save(captor.capture())
        result shouldBe captor.firstValue.id
    }

    test("registerUser_validInput_publishesUserRegisteredEvent") {
        whenever(userRepository.existsByEmail("jane@example.com")).thenReturn(false)
        whenever(userRepository.save(any())).thenAnswer { it.getArgument<User>(0) }
        whenever(userSettingsRepository.save(any())).thenAnswer { it.getArgument<UserSettings>(0) }

        val result = userService.registerUser("Jane", "Doe", "123 Main St", "jane@example.com")

        val captor = argumentCaptor<UserRegisteredEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        captor.firstValue.userId shouldBe result
        captor.firstValue.email shouldBe "jane@example.com"
        captor.firstValue.timestamp shouldNotBe null
    }

    test("registerUser_validRequest_createsDefaultUserSettings") {
        whenever(userRepository.existsByEmail("jane@example.com")).thenReturn(false)
        whenever(userRepository.save(any())).thenAnswer { it.getArgument<User>(0) }
        whenever(userSettingsRepository.save(any())).thenAnswer { it.getArgument<UserSettings>(0) }

        val userId = userService.registerUser("Jane", "Doe", "123 Main St", "jane@example.com")

        val captor = argumentCaptor<UserSettings>()
        verify(userSettingsRepository).save(captor.capture())
        captor.firstValue.userId shouldBe userId
        captor.firstValue.feedType shouldBe FeedType.SYNTHETIC
    }

    test("getActiveUserEmails_activeUsersExist_returnsEmailList") {
        val allUsers = listOf(
            User(id = UUID.randomUUID(), firstName = "Alice", lastName = "A", address = "1 St", email = "alice@example.com", status = UserStatus.ACTIVE),
            User(id = UUID.randomUUID(), firstName = "Bob", lastName = "B", address = "2 St", email = "bob@example.com", status = UserStatus.SUSPENDED),
            User(id = UUID.randomUUID(), firstName = "Carol", lastName = "C", address = "3 St", email = "carol@example.com", status = UserStatus.ACTIVE)
        )
        whenever(userRepository.findAll()).thenReturn(allUsers)

        val result = userService.getActiveUserEmails()

        result shouldContainExactlyInAnyOrder listOf("alice@example.com", "carol@example.com")
    }

    test("getActiveUserEmails_noActiveUsers_returnsEmptyList") {
        val allUsers = listOf(
            User(id = UUID.randomUUID(), firstName = "Dave", lastName = "D", address = "4 St", email = "dave@example.com", status = UserStatus.CLOSED)
        )
        whenever(userRepository.findAll()).thenReturn(allUsers)

        val result = userService.getActiveUserEmails()

        result shouldBe emptyList()
    }

    test("getActiveUserEmails_noUsersAtAll_returnsEmptyList") {
        whenever(userRepository.findAll()).thenReturn(emptyList())

        val result = userService.getActiveUserEmails()

        result shouldBe emptyList()
    }

    test("getUserById_existingId_returnsUser") {
        val user = makeUser()
        val settings = makeSettings()
        whenever(userRepository.findById(validId)).thenReturn(Optional.of(user))
        whenever(userSettingsRepository.findByUserId(validId)).thenReturn(settings)

        val result = userService.getUserById(validId)

        result shouldBe user
        result.settings shouldBe settings
    }

    test("getUserById_unknownId_throwsUserNotFoundException") {
        whenever(userRepository.findById(validId)).thenReturn(Optional.empty())

        shouldThrow<UserNotFoundException> {
            userService.getUserById(validId)
        }
    }

    test("getUserById_settingsNotFound_throwsUserSettingsNotFoundException") {
        val user = makeUser()
        whenever(userRepository.findById(validId)).thenReturn(Optional.of(user))
        whenever(userSettingsRepository.findByUserId(validId)).thenReturn(null)

        shouldThrow<UserSettingsNotFoundException> {
            userService.getUserById(validId)
        }
    }

    test("updateUserSettings_validFeedType_persistsAndEmitsEvent") {
        val settings = makeSettings(feedType = FeedType.SYNTHETIC)
        whenever(userSettingsRepository.findByUserId(validId)).thenReturn(settings)
        whenever(userSettingsRepository.save(any())).thenReturn(settings)

        userService.updateUserSettings(validId, FeedType.REAL)

        val savedCaptor = argumentCaptor<UserSettings>()
        verify(userSettingsRepository).save(savedCaptor.capture())
        savedCaptor.firstValue.feedType shouldBe FeedType.REAL

        val eventCaptor = argumentCaptor<UserSettingsChangedEvent>()
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        eventCaptor.firstValue.userId shouldBe validId
        eventCaptor.firstValue.feedType shouldBe FeedType.REAL // saved.feedType after mutation
    }

    test("updateUserSettings_settingsNotFound_throwsUserSettingsNotFoundException") {
        whenever(userSettingsRepository.findByUserId(validId)).thenReturn(null)

        shouldThrow<UserSettingsNotFoundException> {
            userService.updateUserSettings(validId, FeedType.REAL)
        }

        verify(userSettingsRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    test("updateUserSettings_sameValue_stillPersistsAndEmitsEvent") {
        val settings = makeSettings(feedType = FeedType.SYNTHETIC)
        whenever(userSettingsRepository.findByUserId(validId)).thenReturn(settings)
        whenever(userSettingsRepository.save(any())).thenReturn(settings)

        userService.updateUserSettings(validId, FeedType.SYNTHETIC)

        verify(userSettingsRepository).save(any())
        verify(eventPublisher).publishEvent(any<UserSettingsChangedEvent>())
    }

    test("updateUserSettings_nullFeedType_doesNotChangeFeedTypeButStillSaves") {
        val settings = makeSettings(feedType = FeedType.SYNTHETIC)
        whenever(userSettingsRepository.findByUserId(validId)).thenReturn(settings)
        whenever(userSettingsRepository.save(any())).thenReturn(settings)

        userService.updateUserSettings(validId, null)

        val savedCaptor = argumentCaptor<UserSettings>()
        verify(userSettingsRepository).save(savedCaptor.capture())
        savedCaptor.firstValue.feedType shouldBe FeedType.SYNTHETIC
        verify(eventPublisher).publishEvent(any<UserSettingsChangedEvent>())
    }

    test("loginUser_activeUser_returnsUser") {
        val user = makeUser()
        whenever(userRepository.findByEmail("jane@example.com")).thenReturn(user)

        val result = userService.loginUser("jane@example.com")

        result shouldBe user
    }

    test("loginUser_unknownEmail_throwsUserNotFoundException") {
        whenever(userRepository.findByEmail("ghost@example.com")).thenReturn(null)

        shouldThrow<UserNotFoundException> {
            userService.loginUser("ghost@example.com")
        }
    }

    test("loginUser_suspendedUser_throwsUserNotActiveException") {
        val user = makeUser(status = UserStatus.SUSPENDED)
        whenever(userRepository.findByEmail("sus@example.com")).thenReturn(user)

        shouldThrow<UserNotActiveException> {
            userService.loginUser("sus@example.com")
        }
    }

    test("loginUser_closedUser_throwsUserNotActiveException") {
        val user = makeUser(status = UserStatus.CLOSED)
        whenever(userRepository.findByEmail("clo@example.com")).thenReturn(user)

        shouldThrow<UserNotActiveException> {
            userService.loginUser("clo@example.com")
        }
    }
})
