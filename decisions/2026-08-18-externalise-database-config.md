# Externalise Database Configuration

**Date:** 2026-08-18  
**Issue:** #137  
**Status:** Accepted

---

## Context

The application was using an H2 in-memory database (`jdbc:h2:mem:tradelab`).
All user data was lost on every application restart, which made manual
testing and development workflows painful.

The platform also needs to support cloud deployment where PostgreSQL is the
target database.

---

## Decision

Introduce Spring profile-based database configuration with three modes:

| Profile | Database | Use |
|---------|----------|-----|
| default (no profile) | H2 file-based (`./data/tradelab`) | Local development — data persists across restarts |
| `test` | H2 in-memory | Automated tests — isolated, fast, no file I/O |
| `prod` | PostgreSQL | Cloud / production deployment |

### Local development (default profile)

- Switch from `jdbc:h2:mem:tradelab` to `jdbc:h2:file:./data/tradelab`
- Use `ddl-auto: update` so schema evolves as entities change without wiping data
- H2 console remains enabled for inspection
- The `./data/` directory is added to `.gitignore`

### Test profile

- Restore `jdbc:h2:mem:tradelab;DB_CLOSE_DELAY=-1` in `application-test.yml`
- Use `ddl-auto: create-drop` so every test run starts clean
- All `@DataJpaTest` and `@SpringBootTest` tests pick this up automatically
  when the `test` profile is active

### Production profile (`prod`)

- Full PostgreSQL configuration driven by environment variables:
  - `DATASOURCE_URL` (e.g. `jdbc:postgresql://host:5432/tradelab`)
  - `DATASOURCE_USERNAME`
  - `DATASOURCE_PASSWORD`
- Use `ddl-auto: validate` — schema is managed externally (Flyway or manual
  DDL), Hibernate only validates it at startup
- H2 console disabled

---

## Alternatives considered

**Keep H2 in-memory for dev, add PostgreSQL only for prod** — rejected. The
H2-to-PostgreSQL impedance differences (e.g. auto-increment vs sequences,
case sensitivity) have caught regressions in the past. File-based H2 for dev
avoids this while still giving local persistence.

**Introduce Flyway immediately** — deferred. Schema migration tooling is a
separate concern and should be introduced in a follow-on task once the
PostgreSQL profile is proven in a staging environment.

---

## Consequences

- Developers running the app locally will get persistent data in `./data/tradelab.mv.db`.
- Existing tests remain unaffected — the `test` profile explicitly opts back
  into in-memory H2 with `create-drop`.
- Cloud deployments must supply `DATASOURCE_URL`, `DATASOURCE_USERNAME`, and
  `DATASOURCE_PASSWORD` environment variables and activate the `prod` profile
  (`SPRING_PROFILES_ACTIVE=prod`).
- `standards/backend.md` updated to reflect the three-profile model.
