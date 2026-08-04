package org.dpp.tradelab.marketdata.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import java.net.URI
import java.nio.charset.StandardCharsets

class FinnhubLoggingInterceptorTest : FunSpec({

    val interceptor = FinnhubLoggingInterceptor()

    test("intercept_successResponse_returnsResponseUnmodified") {
        val request = MockClientHttpRequest(HttpMethod.GET, URI("https://finnhub.io/api/v1/quote?symbol=AAPL"))
        val responseBody = """{"c":182.5,"o":180.0,"h":185.0,"l":179.0}""".toByteArray(StandardCharsets.UTF_8)
        val mockResponse: ClientHttpResponse = MockClientHttpResponse(responseBody, HttpStatus.OK)

        val execution = mock<ClientHttpRequestExecution>()
        whenever(execution.execute(request, byteArrayOf())).thenReturn(mockResponse)

        val result = interceptor.intercept(request, byteArrayOf(), execution)

        result.statusCode shouldBe HttpStatus.OK
    }

    test("intercept_withRequestBody_returnsResponseUnmodified") {
        val request = MockClientHttpRequest(HttpMethod.POST, URI("https://finnhub.io/api/v1/test"))
        val body = """{"symbol":"AAPL"}""".toByteArray(StandardCharsets.UTF_8)
        val responseBody = """{"result":"ok"}""".toByteArray(StandardCharsets.UTF_8)
        val mockResponse: ClientHttpResponse = MockClientHttpResponse(responseBody, HttpStatus.OK)

        val execution = mock<ClientHttpRequestExecution>()
        whenever(execution.execute(request, body)).thenReturn(mockResponse)

        val result = interceptor.intercept(request, body, execution)

        result.statusCode shouldBe HttpStatus.OK
    }

    test("intercept_errorResponse_passesResponseThroughUnmodified") {
        val request = MockClientHttpRequest(HttpMethod.GET, URI("https://finnhub.io/api/v1/quote?symbol=UNKNOWN"))
        val responseBody = """{"error":"Not found"}""".toByteArray(StandardCharsets.UTF_8)
        val mockResponse: ClientHttpResponse = MockClientHttpResponse(responseBody, HttpStatus.NOT_FOUND)

        val execution = mock<ClientHttpRequestExecution>()
        whenever(execution.execute(request, byteArrayOf())).thenReturn(mockResponse)

        val result = interceptor.intercept(request, byteArrayOf(), execution)

        result.statusCode shouldBe HttpStatus.NOT_FOUND
    }

    test("intercept_emptyBody_doesNotThrow") {
        val request = MockClientHttpRequest(HttpMethod.GET, URI("https://finnhub.io/api/v1/quote?symbol=AAPL"))
        val mockResponse: ClientHttpResponse = MockClientHttpResponse(byteArrayOf(), HttpStatus.OK)

        val execution = mock<ClientHttpRequestExecution>()
        whenever(execution.execute(request, byteArrayOf())).thenReturn(mockResponse)

        val result = interceptor.intercept(request, byteArrayOf(), execution)

        result.statusCode shouldBe HttpStatus.OK
    }
})
