package org.dpp.tradelab

import org.dpp.tradelab.user.exception.DuplicateEmailException
import org.dpp.tradelab.user.exception.UserNotFoundException
import org.dpp.tradelab.user.exception.UserNotActiveException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

data class ErrorResponse(val status: Int, val error: String, val details: List<String>)

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors.map { "${it.field} ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(ErrorResponse(400, "Validation failed", details))
    }

    @ExceptionHandler(DuplicateEmailException::class)
    fun handleDuplicateEmail(ex: DuplicateEmailException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(409, "Email already registered", listOf(ex.message ?: "An account with this email already exists.")))

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(ex: UserNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(404, "User not found", listOf(ex.message ?: "User not found.")))

    @ExceptionHandler(UserNotActiveException::class)
    fun handleUserNotActive(ex: UserNotActiveException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(403, "Account unavailable", listOf(ex.message ?: "This account is suspended or closed and cannot be used to log in.")))
}
