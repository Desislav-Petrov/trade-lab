package org.dpp.tradelab.user.controller

import org.dpp.tradelab.user.exception.InvalidFeedTypeException
import org.dpp.tradelab.user.generated.api.UsersApiDelegate
import org.dpp.tradelab.user.generated.model.UpdateUserSettingsRequest
import org.dpp.tradelab.user.generated.model.UserResponse
import org.dpp.tradelab.user.generated.model.UserSettingsResponse
import org.dpp.tradelab.user.model.FeedType
import org.dpp.tradelab.user.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Controller
class UserApiDelegateImpl(
    private val userService: UserService
) : UsersApiDelegate {

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
}
