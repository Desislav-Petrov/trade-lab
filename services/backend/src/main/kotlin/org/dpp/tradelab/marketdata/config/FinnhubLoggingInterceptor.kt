package org.dpp.tradelab.marketdata.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Spring [ClientHttpRequestInterceptor] that logs outbound HTTP traffic for the
 * Finnhub [org.springframework.web.client.RestClient] at DEBUG level.
 *
 * When the logger is NOT at DEBUG level this interceptor is a transparent pass-through
 * with zero overhead — all logging calls are guarded by [org.slf4j.Logger.isDebugEnabled].
 *
 * The response body is readable because the [org.springframework.web.client.RestClient]
 * bean is configured with a [org.springframework.http.client.BufferingClientHttpRequestFactory],
 * which caches the body so it can be read multiple times without exhausting the stream.
 *
 * Logged fields per request/response cycle:
 * - Request: HTTP method, URI, body (UTF-8)
 * - Response: HTTP status code + text, body (UTF-8)
 */
class FinnhubLoggingInterceptor : ClientHttpRequestInterceptor {

    private val logger = LoggerFactory.getLogger(FinnhubLoggingInterceptor::class.java)

    @Throws(IOException::class)
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        if (logger.isDebugEnabled) {
            val requestBody = body.toString(StandardCharsets.UTF_8)
            logger.debug(
                "Finnhub --> {} {} | body: {}",
                request.method,
                request.uri,
                requestBody.ifBlank { "<empty>" }
            )
        }

        val response = execution.execute(request, body)

        if (logger.isDebugEnabled) {
            val responseBody = response.body.readBytes().toString(StandardCharsets.UTF_8)
            logger.debug(
                "Finnhub <-- {} {} | body: {}",
                response.statusCode.value(),
                response.statusText,
                responseBody.ifBlank { "<empty>" }
            )
        }

        return response
    }
}
