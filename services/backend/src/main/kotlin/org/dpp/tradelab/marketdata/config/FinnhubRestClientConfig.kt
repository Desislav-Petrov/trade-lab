package org.dpp.tradelab.marketdata.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Produces the [RestClient] bean used by [org.dpp.tradelab.marketdata.service.FinnhubPriceFeedAdapter]
 * to call the Finnhub `/quote` endpoint.
 *
 * The client is configured with:
 * - The Finnhub base URL and `X-Finnhub-Token` default header.
 * - A [FinnhubLoggingInterceptor] that logs request/response details at DEBUG level.
 * - A [BufferingClientHttpRequestFactory] so the response body can be read by the
 *   interceptor without exhausting the stream before Jackson deserialises it.
 */
@Configuration
class FinnhubRestClientConfig(
    @Value("\${finnhub.api-key}") private val apiKey: String
) {

    @Bean("finnhubRestClient")
    fun finnhubRestClient(): RestClient =
        RestClient.builder()
            .baseUrl("https://finnhub.io/api/v1")
            .defaultHeader("X-Finnhub-Token", apiKey)
            .requestFactory(BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()))
            .requestInterceptor(FinnhubLoggingInterceptor())
            .build()
}
