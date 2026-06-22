# Decision: Pre-assigned UUIDs with Persistable<UUID> for JPA entities

**Date:** 2026-06-22
**Status:** accepted

## Context

`standards/backend.md` specifies `@GeneratedValue(strategy = GenerationType.UUID)` for all entity primary keys. When the `openAccount` service method pre-generated a UUID before calling `save()` (required so the default name `"account-{id}"` could be resolved in a single save), the non-null `id` caused Spring Data's `SimpleJpaRepository.save()` to call `EntityManager.merge()` instead of `EntityManager.persist()`. Hibernate then issued an `UPDATE` before the `INSERT` — found no matching row — and threw `StaleObjectStateException` at runtime.

Additionally, using `data class` for JPA entities is problematic:
- Kotlin auto-generates a `getId()` JVM getter for any property named `id`, which clashes with `Persistable<UUID>.getId()` at compile time.
- `data class` `equals`/`hashCode` is derived from all constructor properties — Hibernate requires identity based on the primary key only. Mutable fields (e.g. `balance`, `status`) would break Set/Map semantics and cause incorrect behaviour with the first-level cache.
- Hibernate uses subclass proxies for lazy loading; `data class` `copy()` and structural equality break on proxies.

## Decision

All JPA entity classes use plain `class` (not `data class`). Entities implement `org.springframework.data.domain.Persistable<UUID>` with a `@Transient _isNew: Boolean = true` flag. This tells Spring Data to always call `persist()` regardless of whether `id` is null, making pre-assigned UUIDs safe. The `@GeneratedValue` annotation is removed — the UUID is always assigned by the caller (service layer) before construction.

`equals` and `hashCode` are implemented manually based on `id` only, satisfying the JPA identity contract.

`toString` is implemented manually for debuggability.

## Consequences

- `@GeneratedValue` is removed from all entity classes. The service layer is responsible for generating UUIDs via `UUID.randomUUID()` before constructing the entity.
- Default name resolution (`name ?: id.toString()`) happens in the service before the single `save()` call — no two-save pattern required.
- `standards/backend.md` is updated: the `@GeneratedValue` instruction is replaced with the `Persistable<UUID>` pattern.
- All future entity classes must follow this pattern: plain `class`, implement `Persistable<UUID>`, pre-assign UUID in the service.
