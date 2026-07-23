package org.dpp.tradelab.user.service

import org.dpp.tradelab.user.api.UserLookupApi
import org.dpp.tradelab.user.exception.DuplicateEmailException
import org.dpp.tradelab.user.exception.UserNotFoundException
import org.dpp.tradelab.user.exception.UserNotActiveException
import org.dpp.tradelab.user.exception.UserSettingsNotFoundException
import org.dpp.tradelab.user.messaging.UserRegisteredEvent
import org.dpp.tradelab.user.messaging.UserLoggedInEvent
import org.dpp.tradelab.user.messaging.UserSettingsChangedEvent
import org.dpp.tradelab.user.model.FeedType
import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserSettings
import org.dpp.tradelab.user.model.UserStatus
import org.dpp.tradelab.user.repository.UserRepository
import org.dpp.tradelab.user.repository.UserSettingsRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val eventPublisher: ApplicationEventPublisher
) : UserLookupApi {

    @Transactional(readOnly = true)
    override fun existsById(userId: UUID): Boolean = userRepository.existsById(userId)

    @Transactional
    fun registerUser(firstName: String, lastName: String, address: String, email: String): UUID {
        if (userRepository.existsByEmail(email)) {
            throw DuplicateEmailException("An account with this email already exists.")
        }

        val id = UUID.randomUUID()
        userRepository.save(
            User(
                id = id,
                firstName = firstName,
                lastName = lastName,
                address = address,
                email = email
            )
        )

        userSettingsRepository.save(
            UserSettings(
                id = UUID.randomUUID(),
                userId = id,
                feedType = FeedType.SYNTHETIC
            )
        )

        eventPublisher.publishEvent(
            UserRegisteredEvent(
                userId = id,
                email = email,
                timestamp = Instant.now()
            )
        )

        return id
    }

    @Transactional(readOnly = true)
    fun getActiveUserEmails(): List<String> =
        userRepository.findAll()
            .filter { it.status == UserStatus.ACTIVE }
            .map { it.email }

    @Transactional(readOnly = true)
    fun getUserById(userId: UUID): User {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }
        user.settings = userSettingsRepository.findByUserId(userId)
            ?: throw UserSettingsNotFoundException(userId)
        return user
    }

    @Transactional
    fun updateUserSettings(userId: UUID, feedType: FeedType?): UserSettings {
        val settings = userSettingsRepository.findByUserId(userId)
            ?: throw UserSettingsNotFoundException(userId)

        if (feedType != null) {
            settings.feedType = feedType
        }

        val saved = userSettingsRepository.save(settings)

        eventPublisher.publishEvent(
            UserSettingsChangedEvent(
                userId = userId,
                feedType = saved.feedType,
                updatedAt = saved.updatedAt ?: Instant.now()
            )
        )

        return saved
    }

    @Transactional(readOnly = true)
    fun loginUser(email: String): User {
        val user = userRepository.findByEmail(email)
            ?: throw UserNotFoundException(UUID.fromString("00000000-0000-0000-0000-000000000000"))
        if (user.status != UserStatus.ACTIVE) {
            throw UserNotActiveException(email)
        }
        eventPublisher.publishEvent(
            UserLoggedInEvent(
                userId = user.id,
                email = user.email,
                timestamp = Instant.now()
            )
        )
        return user
    }
}
