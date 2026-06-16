package org.dpp.tradelab.user.api

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class RegisterUserRequest(
    @field:NotBlank
    val firstName: String,

    @field:NotBlank
    val lastName: String,

    @field:NotBlank
    val address: String,

    @field:NotBlank
    @field:Email
    val email: String
)

data class RegisterUserResponse(
    val userId: UUID
)
