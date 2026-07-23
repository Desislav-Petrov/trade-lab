package org.dpp.tradelab.user.messaging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe

class UserEventListenerTest : FunSpec({

    test("userEventListener_instantiatesCorrectly") {
        val listener = UserEventListener()
        listener shouldNotBe null
    }
})
