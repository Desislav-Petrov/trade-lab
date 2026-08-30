package org.dpp.tradelab.user.controller

import org.dpp.tradelab.user.generated.noauth.api.UsersNoAuthApiDelegate
import org.dpp.tradelab.user.generated.noauth.model.LoginRequest
import org.dpp.tradelab.user.generated.noauth.model.LoginTokenResponse
import org.dpp.tradelab.user.generated.noauth.model.RegisterUserRequest
import org.dpp.tradelab.user.generated.noauth.model.RegisterUserResponse
import org.dpp.tradelab.user.generated.noauth.model.UserEmailsResponse
import org.dpp.tradelab.user.service.UserService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["app.features.enable-no-auth"], havingValue = "true", matchIfMissing = false)
class UserNoAuthApiDelegateImpl(
    private val userService: UserService
) : UsersNoAuthApiDelegate {

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

    override fun loginUser(loginRequest: LoginRequest): ResponseEntity<LoginTokenResponse> {
        val jwt = userService.loginUser(loginRequest.email)
        return ResponseEntity.ok(LoginTokenResponse(token = jwt))
    }
}
