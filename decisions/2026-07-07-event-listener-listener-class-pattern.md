# Decision: @EventListener methods must live in a dedicated listener class

**Date:** 2026-07-07
**Status:** Accepted

---

## Context

Spring's `@EventListener` (and `@TransactionalEventListener`) wires event
handling at the method level. Before this decision, listener methods were placed
directly on service classes (e.g. `MarketDataFeedService` contained
`onAssetSubscribed` and `onAssetUnsubscribed` annotated with `@EventListener`).

This conflates two responsibilities inside the service class:
1. Business logic (what to *do*)
2. Event reception (how the trigger *arrives*)

The platform is designed as a modular monolith with an explicit migration path
to microservices (see `standards/architecture.md` — Future Service Extraction).
When a domain is extracted, Spring Application Events become Kafka consumers (or
equivalent). If the listener annotation lives inside the service class, that
extraction requires modifying business logic files — violating the goal of
"deployment and wiring changes only".

---

## Decision

`@EventListener` and `@TransactionalEventListener` annotations are **never**
placed on service class methods. They must only appear on methods inside a
dedicated listener class that lives in `{domain}.messaging`.

### Naming convention

| Scenario | Class name |
|---|---|
| Domain listening to its own events | `{Domain}EventListener` |
| Domain listening to events from another domain | `{PublishingDomain}EventListener` |

### Structural rules

1. The listener class is annotated `@Component` (not `@Service`).
2. The listener class receives the relevant service(s) via constructor injection.
3. Each `@EventListener` method does **one thing only**: call a single service
   method. No business logic, no conditionals, no repository access inside the
   listener method itself.
4. The corresponding service method is named `handle{EventName}` to make the
   delegation chain explicit (e.g. `handleAssetSubscribed`,
   `handleAssetUnsubscribed`).
5. The listener class has no state of its own.

### Example

```kotlin
// marketdata/messaging/MarketDataEventListener.kt
@Component
class MarketDataEventListener(
    private val marketDataFeedService: MarketDataFeedService
) {
    @EventListener
    fun onAssetSubscribed(event: AssetSubscribedEvent) =
        marketDataFeedService.handleAssetSubscribed(event)

    @EventListener
    fun onAssetUnsubscribed(event: AssetUnsubscribedEvent) =
        marketDataFeedService.handleAssetUnsubscribed(event)
}
```

```kotlin
// marketdata/service/MarketDataFeedService.kt
fun handleAssetSubscribed(event: AssetSubscribedEvent) { ... }
fun handleAssetUnsubscribed(event: AssetUnsubscribedEvent) { ... }
```

---

## Kafka migration path

When the domain is extracted to a standalone service, the only file that changes
is the listener class: the `@EventListener` annotations are replaced with a
Kafka `@KafkaListener` (or equivalent). The service's `handle*` methods are
unchanged.

---

## Consequences

- `MarketDataFeedService` had its two `@EventListener` methods extracted into a
  new `MarketDataEventListener` class in `marketdata.messaging` as the first
  application of this rule.
- `standards/backend.md` updated to codify this pattern.
- `standards/architecture.md` table updated to reflect that `messaging/`
  contains listener classes (not just event definitions and publishers).
