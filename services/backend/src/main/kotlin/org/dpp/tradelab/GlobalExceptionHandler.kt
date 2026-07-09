package org.dpp.tradelab

import org.dpp.tradelab.ledger.exception.AccountNotActiveException
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.ledger.exception.AccountOwnershipException
import org.dpp.tradelab.ledger.exception.InvalidCurrencyException
import org.dpp.tradelab.ledger.exception.UserNotFoundException as LedgerUserNotFoundException
import org.dpp.tradelab.marketdata.exception.SubscriptionLimitExceededException
import org.dpp.tradelab.marketdata.exception.SubscriptionNotFoundException
import org.dpp.tradelab.marketdata.exception.TickerAlreadySubscribedException
import org.dpp.tradelab.marketdata.exception.UnsupportedTickerException
import org.dpp.tradelab.stocktrading.exception.DuplicateIdempotencyKeyException
import org.dpp.tradelab.stocktrading.exception.InsufficientFundsException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotActiveException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotFoundException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotOwnedException
import org.dpp.tradelab.stocktrading.exception.TickerNotFoundException
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

    @ExceptionHandler(InvalidCurrencyException::class)
    fun handleInvalidCurrency(ex: InvalidCurrencyException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, "Invalid currency", listOf(ex.message ?: "The provided currency is not supported.")))

    @ExceptionHandler(LedgerUserNotFoundException::class)
    fun handleLedgerUserNotFound(ex: LedgerUserNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(403, "User not found", listOf(ex.message ?: "User not found.")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, "Bad request", listOf(ex.message ?: "Invalid request.")))

    @ExceptionHandler(AccountNotFoundException::class)
    fun handleAccountNotFound(ex: AccountNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(404, "Account not found", listOf(ex.message ?: "Account not found.")))

    @ExceptionHandler(AccountOwnershipException::class)
    fun handleAccountOwnership(ex: AccountOwnershipException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(403, "Account ownership violation", listOf(ex.message ?: "Account does not belong to the requesting user.")))

    @ExceptionHandler(AccountNotActiveException::class)
    fun handleAccountNotActive(ex: AccountNotActiveException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(403, "Account not available", listOf(ex.message ?: "Account is not available for this operation.")))

    @ExceptionHandler(TickerAlreadySubscribedException::class)
    fun handleTickerAlreadySubscribed(ex: TickerAlreadySubscribedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(409, "Ticker already subscribed", listOf(ex.message ?: "One or more tickers are already subscribed.")))

    @ExceptionHandler(UnsupportedTickerException::class)
    fun handleUnsupportedTicker(ex: UnsupportedTickerException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, "Unsupported ticker", listOf(ex.message ?: "One or more tickers are not in the supported list.")))

    @ExceptionHandler(SubscriptionLimitExceededException::class)
    fun handleSubscriptionLimitExceeded(ex: SubscriptionLimitExceededException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(422, "Subscription limit exceeded", listOf(ex.message ?: "Adding these tickers would exceed your 1000 subscription limit.")))

    @ExceptionHandler(SubscriptionNotFoundException::class)
    fun handleSubscriptionNotFound(ex: SubscriptionNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(404, "Subscription not found", listOf(ex.message ?: "No subscription found for the given ticker.")))

    // ── Stock Trading exceptions ─────────────────────────────────────────────

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(422, "Insufficient funds", listOf(ex.message ?: "Insufficient funds.")))

    @ExceptionHandler(TickerNotFoundException::class)
    fun handleTickerNotFound(ex: TickerNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, "Ticker not found", listOf(ex.message ?: "Ticker not found in supported list.")))

    @ExceptionHandler(DuplicateIdempotencyKeyException::class)
    fun handleDuplicateIdempotencyKey(ex: DuplicateIdempotencyKeyException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(409, "Duplicate idempotency key", listOf(ex.message ?: "An order with this idempotency key has already been submitted.")))

    @ExceptionHandler(OrderAccountNotFoundException::class)
    fun handleOrderAccountNotFound(ex: OrderAccountNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(404, "Account not found", listOf(ex.message ?: "Account not found.")))

    @ExceptionHandler(OrderAccountNotOwnedException::class)
    fun handleOrderAccountNotOwned(ex: OrderAccountNotOwnedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(403, "Account not owned", listOf(ex.message ?: "Account does not belong to the requesting user.")))

    @ExceptionHandler(OrderAccountNotActiveException::class)
    fun handleOrderAccountNotActive(ex: OrderAccountNotActiveException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(403, "Account not active", listOf(ex.message ?: "Account is not active.")))

    // ── Portfolio exceptions ──────────────────────────────────────────────────

    @ExceptionHandler(PortfolioAccountNotFoundException::class)
    fun handlePortfolioAccountNotFound(ex: PortfolioAccountNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(404, "Account not found", listOf(ex.message ?: "Account not found.")))

    @ExceptionHandler(PortfolioAccountAccessDeniedException::class)
    fun handlePortfolioAccountAccessDenied(ex: PortfolioAccountAccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(403, "Account access denied", listOf(ex.message ?: "You do not have access to this account.")))

    @ExceptionHandler(PortfolioPriceUnavailableException::class)
    fun handlePortfolioPriceUnavailable(ex: PortfolioPriceUnavailableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse(502, "Price data unavailable", listOf(ex.message ?: "Unable to retrieve price data.")))

    @ExceptionHandler(PortfolioBalanceUnavailableException::class)
    fun handlePortfolioBalanceUnavailable(ex: PortfolioBalanceUnavailableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse(502, "Balance data unavailable", listOf(ex.message ?: "Unable to retrieve balance data.")))
}
