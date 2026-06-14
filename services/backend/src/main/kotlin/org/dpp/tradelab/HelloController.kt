package org.dpp.tradelab

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class HelloResponse(val message: String)

@RestController
@RequestMapping("/api/v1")
class HelloController {

    @GetMapping("/hello")
    fun hello(): ResponseEntity<HelloResponse> =
        ResponseEntity.ok(HelloResponse("Hello, World!"))
}
