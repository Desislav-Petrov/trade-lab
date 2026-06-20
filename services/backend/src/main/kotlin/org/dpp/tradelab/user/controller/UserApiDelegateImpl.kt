package org.dpp.tradelab.user.controller

import org.dpp.tradelab.user.generated.api.UsersApiDelegate
import org.dpp.tradelab.user.generated.model.RegisterUserRequest
import org.dpp.tradelab.user.generated.model.RegisterUserResponse
import org.dpp.tradelab.user.generated.model.UserEmailsResponse
import org.dpp.tradelab.user.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller

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
}
