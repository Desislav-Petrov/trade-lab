package org.dpp.tradelab.user.controller

import org.dpp.tradelab.user.exception.InvalidFeedTypeException
import org.dpp.tradelab.user.generated.api.UsersApiDelegate
import org.dpp.tradelab.user.generated.model.LoginRequest
import org.dpp.tradelab.user.generated.model.LoginTokenResponse
import org.dpp.tradelab.user.generated.model.RegisterUserRequest
import org.dpp.tradelab.user.generated.model.RegisterUserResponse
import org.dpp.tradelab.user.generated.model.UpdateUserSettingsRequest
import org.dpp.tradelab.user.generated.model.UserEmailsResponse
import org.dpp.tradelab.user.generated.model.UserResponse
import org.dpp.tradelab.user.generated.model.UserSettingsResponse
import org.dpp.tradelab.user.model.FeedType
import org.dpp.tradelab.user.model.UserStatus
import org.dpp.tradelab.user.service.UserService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
@Primary
class UserApiDelegateImpl(
    private val userService: UserService,
    private val noAuthUserApiDelegate: ObjectProvider<NoAuthUserApiDelegateImpl>
) : UsersApiDelegate {

    override fun registerUser(
        registerUserRequest: RegisterUserRequest
    ): ResponseEntity<RegisterUserResponse> {
        return noAuthUserApiDelegate.ifAvailable
            ?.registerUser(registerUserRequest)
            ?: ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }

    override fun getActiveUserEmails(): ResponseEntity<UserEmailsResponse> {
        return noAuthUserApiDelegate.ifAvailable
            ?.getActiveUserEmails()
            ?: ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }

    override fun getUserById(userId: UUID): ResponseEntity<UserResponse> {
        val user = userService.getUserById(userId)
        val settings = user.settings
        val status = when (user.status) {
            UserStatus.ACTIVE -> UserResponse.Status.ACTIVE
            UserStatus.SUSPENDED -> UserResponse.Status.SUSPENDED
            UserStatus.CLOSED -> UserResponse.Status.CLOSED
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
        loginRequest: LoginRequest
    ): ResponseEntity<LoginTokenResponse> {
        return noAuthUserApiDelegate.ifAvailable
            ?.loginUser(loginRequest)
            ?: ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }
}
