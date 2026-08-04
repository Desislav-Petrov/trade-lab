# Decision: Finnhub RestClient HTTP Logging Interceptor

**Date:** 2026-08-04  
**Status:** accepted

## Context

The `FinnhubPriceFeedAdapter` creates a `RestClient` inline inside its `quoteSupplier` lambda,
making it impossible to add cross-cutting concerns (e.g. logging, metrics) without modifying
the adapter itself. When debugging Finnhub integration issues there is no visibility into
the actual HTTP traffic (path, request body, HTTP status, response body).

## Decision

- Introduce a `FinnhubLoggingInterceptor` (implements `ClientHttpRequestInterceptor`) in
  `marketdata/config/`. It logs request method, URI, request body, response status, and
  response body at `DEBUG` level. Logging is guarded by `logger.isDebugEnabled` so it is
  a no-op in production unless the logger level is explicitly set to `DEBUG`.
- Use `BufferingClientHttpRequestFactory` to wrap the underlying `SimpleClientHttpRequestFactory`
  so that the response body can be read by the interceptor without exhausting the stream
  before the caller deserialises it.
- Extract a `FinnhubRestClientConfig` (`@Configuration`) in `marketdata/config/` that
  produces a `RestClient` bean qualified as `finnhubRestClient`. The adapter receives this
  bean via constructor injection.
- The `quoteSupplier` field on `FinnhubPriceFeedAdapter` remains `internal var` so that
  existing unit tests can still override it without needing a real HTTP call.
- No retry or circuit-breaker logic is added — failed calls are still silently dropped
  per the existing decision (`2026-08-03-real-market-feed-finnhub.md`).

## Consequences

- `FinnhubPriceFeedAdapter` no longer creates its own `RestClient`; it receives one via
  injection. The test helper `buildAdapter(...)` is updated to pass a `RestClient` mock
  so the existing tests remain valid.
- To enable HTTP-level debug logging in development, set:
  ```yaml
  logging:
    level:
      org.dpp.tradelab.marketdata.config.FinnhubLoggingInterceptor: DEBUG
  ```
- Response bodies are buffered in memory — acceptable because Finnhub `/quote` responses
  are small (< 200 bytes). This should be revisited if a streaming endpoint is ever added.
