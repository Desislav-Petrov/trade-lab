package org.dpp.tradelab.user.service

import org.dpp.tradelab.user.exception.DuplicateEmailException
import org.dpp.tradelab.user.messaging.UserRegisteredEvent
import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserStatus
import org.dpp.tradelab.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional
    fun registerUser(firstName: String, lastName: String, address: String, email: String): UUID {
        if (userRepository.existsByEmail(email)) {
            throw DuplicateEmailException("An account with this email already exists.")
        }

        val user = userRepository.save(
            User(
                firstName = firstName,
                lastName = lastName,
                address = address,
                email = email
            )
        )

        eventPublisher.publishEvent(
            UserRegisteredEvent(
                userId = user.id!!,
                email = user.email,
                timestamp = Instant.now()
            )
        )

        return user.id!!
    }

    @Transactional(readOnly = true)
    fun getActiveUserEmails(): List<String> =
        userRepository.findAllByStatus(UserStatus.ACTIVE).map { it.email }
}
