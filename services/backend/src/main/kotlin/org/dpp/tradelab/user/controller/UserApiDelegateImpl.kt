package org.dpp.tradelab.user.controller

import org.dpp.tradelab.user.generated.api.UsersApiDelegate
import org.dpp.tradelab.user.generated.model.LoginResponse
import org.dpp.tradelab.user.generated.model.RegisterUserRequest
import org.dpp.tradelab.user.generated.model.RegisterUserResponse
import org.dpp.tradelab.user.generated.model.UserEmailsResponse
import org.dpp.tradelab.user.generated.model.UserResponse
import org.dpp.tradelab.user.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class UserApiDelegateImpl(private val userService: UserService) : UsersApiDelegate {

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
        val status = when (user.status.name.lowercase()) {
            "active" -> UserResponse.Status.active
            "suspended" -> UserResponse.Status.suspended
            else -> UserResponse.Status.closed
        }
        return ResponseEntity.ok(
            UserResponse(
                userId = user.id!!,
                firstName = user.firstName,
                lastName = user.lastName,
                address = user.address,
                email = user.email,
                status = status,
                createdAt = user.createdAt!!
            )
        )
    }

    override fun loginUser(
        loginRequest: org.dpp.tradelab.user.generated.model.LoginRequest
    ): ResponseEntity<LoginResponse> {
        val user = userService.loginUser(loginRequest.email)
        return ResponseEntity.ok(
            LoginResponse(
                userId = user.id!!,
                email = user.email
            )
        )
    }
}
