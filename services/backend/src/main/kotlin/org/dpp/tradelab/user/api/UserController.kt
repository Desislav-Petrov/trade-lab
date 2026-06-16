package org.dpp.tradelab.user.api

import org.dpp.tradelab.user.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.validation.Valid

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    @PostMapping
    fun registerUser(@Valid @RequestBody request: RegisterUserRequest): ResponseEntity<RegisterUserResponse> {
        val userId = userService.registerUser(
            firstName = request.firstName,
            lastName = request.lastName,
            address = request.address,
            email = request.email
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterUserResponse(userId))
    }
}
