package org.dpp.tradelab.marketdata.exception

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class WebSocketAuthExceptionTest : FunSpec({

    test("webSocketAuthException_message_isPropagatedCorrectly") {
        val message = "userId required"
        val ex = WebSocketAuthException(message)

        ex.message shouldBe message
    }

    test("webSocketAuthException_isInstanceOfRuntimeException") {
        val ex = WebSocketAuthException("some auth error")

        ex.shouldBeInstanceOf<RuntimeException>()
    }

    test("webSocketFeedException_message_isPropagatedCorrectly") {
        val message = "internal error"
        val ex = WebSocketFeedException(message)

        ex.message shouldBe message
    }

    test("webSocketFeedException_isInstanceOfRuntimeException") {
        val ex = WebSocketFeedException("some feed error")

        ex.shouldBeInstanceOf<RuntimeException>()
    }
})
