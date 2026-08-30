package org.dpp.tradelab.user.controller

import org.dpp.tradelab.user.exception.InvalidFeedTypeException
import org.dpp.tradelab.user.generated.api.UsersApiDelegate
import org.dpp.tradelab.user.generated.model.RegisterUserRequest
import org.dpp.tradelab.user.generated.model.RegisterUserResponse
import org.dpp.tradelab.user.generated.model.UpdateUserSettingsRequest
import org.dpp.tradelab.user.generated.model.UserEmailsResponse
import org.dpp.tradelab.user.generated.model.UserResponse
import org.dpp.tradelab.user.generated.model.UserSettingsResponse
import org.dpp.tradelab.user.model.FeedType
import org.dpp.tradelab.user.service.UserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Controller
class UserApiDelegateImpl(
    private val userService: UserService,
    @Value("\${app.frontend.origin}")
    private val frontendOrigin: String
) : UsersApiDelegate {

    override fun registerUser(registerUserRequest: RegisterUserRequest): ResponseEntity<RegisterUserResponse> {
        val userId = userService.registerUser(
            firstName = registerUserRequest.firstName,
            lastName = registerUserRequest.lastName,
            address = registerUserRequest.address,
            email = registerUserRequest.email
        )
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(RegisterUserResponse(userId = userId))
    }

    override fun getActiveUserEmails(): ResponseEntity<UserEmailsResponse> {
        val emails = userService.getActiveUserEmails()
        return ResponseEntity.ok(UserEmailsResponse(emails = emails))
    }

    override fun getUserById(userId: UUID): ResponseEntity<UserResponse> {
        val user = userService.getUserById(userId)
        val settings = user.settings
        val status = when (user.status) {
            org.dpp.tradelab.user.model.UserStatus.ACTIVE -> UserResponse.Status.ACTIVE
            org.dpp.tradelab.user.model.UserStatus.SUSPENDED -> UserResponse.Status.SUSPENDED
            org.dpp.tradelab.user.model.UserStatus.CLOSED -> UserResponse.Status.CLOSED
        }
        val addressOrEmpty: String = user.address ?: ""
        return ResponseEntity.ok(
            UserResponse(
                userId = user.id,
                firstName = user.firstName,
                lastName = user.lastName,
                address = addressOrEmpty,
                email = user.email,
                status = status,
                createdAt = OffsetDateTime.ofInstant(user.createdAt, ZoneOffset.UTC),
                settings = UserSettingsResponse(
                    feedType = UserSettingsResponse.FeedType.valueOf(settings.feedType.name),
                    updatedAt = OffsetDateTime.ofInstant(settings.updatedAt!!, ZoneOffset.UTC)
                )
            )
        )
    }

    override fun updateUserSettings(
        userId: UUID,
        updateUserSettingsRequest: UpdateUserSettingsRequest
    ): ResponseEntity<UserSettingsResponse> {
        val feedType = updateUserSettingsRequest.feedType?.let { feedTypeStr ->
            try {
                FeedType.valueOf(feedTypeStr.value)
            } catch (e: IllegalArgumentException) {
                throw InvalidFeedTypeException(feedTypeStr.value)
            }
        }
        val settings = userService.updateUserSettings(userId, feedType)
        return ResponseEntity.ok(
            UserSettingsResponse(
                feedType = UserSettingsResponse.FeedType.valueOf(settings.feedType.name),
                updatedAt = OffsetDateTime.ofInstant(settings.updatedAt!!, ZoneOffset.UTC)
            )
        )
    }

    override fun loginUser(
        loginRequest: org.dpp.tradelab.user.generated.model.LoginRequest
    ): ResponseEntity<Unit> {
        val jwt = userService.loginUser(loginRequest.email)
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI("$frontendOrigin/auth/callback?token=$jwt"))
            .build()
    }
}
