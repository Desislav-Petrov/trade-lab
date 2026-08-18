# trade-lab
Paper trading platform

## Running the Backend

### Prerequisites

- JDK 21 or above

### Start

```bash
cd services/backend
./gradlew bootRun
```

The application starts on `http://localhost:8080`.

Verify it is running:

```
GET http://localhost:8080/api/v1/hello
```

The H2 console is available at `http://localhost:8080/h2-console`  
(JDBC URL: `jdbc:h2:file:./data/tradelab`, no credentials required).

Data is persisted in `./data/tradelab.mv.db` and survives restarts.  
To switch to a different database, set the `DATASOURCE_URL` environment variable before starting (see [Database configuration](#database-configuration) below).

### Other Gradle tasks

| Task | Purpose |
|---|---|
| `./gradlew test` | Run all tests |
| `./gradlew build` | Compile, test, and package |

---

## Spring Boot Admin

Spring Boot Admin provides a web UI for monitoring and managing the running application.

**URL:** `http://localhost:8080/admin`

Open it in a browser after starting the backend. The application registers itself automatically — no separate admin server process is needed.

### What you can do from the UI

| Section | What it shows / lets you do |
|---|---|
| **Health** | Component health status (DB, disk, etc.) |
| **Details** | JVM info, uptime, build info |
| **Metrics** | Live graphs of JVM memory, CPU, HTTP request rates, and more |
| **Environment** | All resolved config properties (secret values redacted) |
| **Loggers** | View and change log levels per package without restarting |
| **Mappings** | All registered HTTP routes |
| **Threads** | Live thread dump |
| **Beans** | All Spring beans in the application context |

### Changing a log level via the UI

1. Open `http://localhost:8080/admin`.
2. Click on the **trade-lab** instance.
3. Go to **Loggers**.
4. Find `org.dpp.tradelab` and change the level to `DEBUG`.

Changes take effect immediately with no restart required.

---

## Spring Boot Actuator

The backend also exposes raw operational endpoints via Spring Boot Actuator at the `/actuator` base path (the Admin UI is built on top of these).

### Available endpoints

| Endpoint | URL | What it shows |
|---|---|---|
| Health | `GET /actuator/health` | Application health status and component details |
| Info | `GET /actuator/info` | App name and description |
| Metrics index | `GET /actuator/metrics` | List of all available metric names |
| Single metric | `GET /actuator/metrics/{name}` | Value for a specific metric (e.g. `jvm.memory.used`) |
| Mappings | `GET /actuator/mappings` | All registered HTTP endpoints — shows every route the app exposes |
| Environment | `GET /actuator/env` | All resolved configuration properties (values redacted for security) |
| Beans | `GET /actuator/beans` | All Spring beans registered in the application context |
| Loggers | `GET /actuator/loggers` | Current log levels; supports `POST` to change them at runtime |
| Thread dump | `GET /actuator/threaddump` | Current JVM thread dump |
| Conditions | `GET /actuator/conditions` | Auto-configuration conditions evaluation report |

### Common uses

**See all routes the app exposes:**
```
GET http://localhost:8080/actuator/mappings
```

**Check application health:**
```
GET http://localhost:8080/actuator/health
```

**List available metrics:**
```
GET http://localhost:8080/actuator/metrics
```

**Query a specific metric (e.g. heap memory used):**
```
GET http://localhost:8080/actuator/metrics/jvm.memory.used
```

**Change log level at runtime (no restart required):**
```
POST http://localhost:8080/actuator/loggers/org.dpp.tradelab
Content-Type: application/json

{ "configuredLevel": "DEBUG" }
```

---

## Running the Frontend

### Prerequisites

- Node.js 20 or above
- pnpm 11 or above (`npm install -g pnpm` or enable via Corepack: `corepack enable`)

### Install dependencies

```bash
cd services/front-end
pnpm install
```

### Start

```bash
pnpm run dev
```

The application starts on `http://localhost:5173`.

The dev server proxies all `/api` requests to the backend at `http://localhost:8080`. Start the backend first.

### Other pnpm tasks

| Task | Purpose |
|---|---|
| `pnpm run build` | Production build (output: `dist/`) |
| `pnpm run test` | Run all tests |
| `pnpm run lint` | Run Oxlint |
| `pnpm run format` | Format source files with Oxfmt |
| `pnpm run format:check` | Check formatting with Oxfmt without writing |

---

## Creating a Release

Releases are fully automated via GitHub Actions. To publish a new version:

1. Open [GitHub Actions](https://github.com/Desislav-Petrov/trade-lab/actions) and select the **"Create Version"** workflow.
2. Click **"Run workflow"**, enter a semantic version (e.g. `1.0.0`), and click **"Run"**.
3. The pipeline will automatically build backend and frontend Docker images, push them to GHCR, and create a GitHub release.

Docker images are published to:
- `ghcr.io/desislav-petrov/trade-lab-backend:vX.Y.Z`
- `ghcr.io/desislav-petrov/trade-lab-frontend:vX.Y.Z`

For the full guide (local validation, troubleshooting, commit conventions) see **[RELEASE.md](RELEASE.md)**.

---

## Important Configuration Settings

### Database configuration {#database-configuration}

The backend ships with three Spring profiles for database connectivity:

| Profile | Database | `ddl-auto` | When used |
|---|---|---|---|
| *(default)* | H2 file `./data/tradelab` | `update` | Local dev — data persists across restarts |
| `test` | H2 in-memory | `create-drop` | Automated tests (`./gradlew test`) |
| `prod` | PostgreSQL | `validate` | Cloud / production |

#### Switching to a custom local H2 path

Override the datasource URL with the `DATASOURCE_URL` environment variable:

```bash
DATASOURCE_URL=jdbc:h2:file:/path/to/my/db ./gradlew bootRun
```

#### Running against PostgreSQL in production

Set the following environment variables before starting the application:

```bash
SPRING_PROFILES_ACTIVE=prod
DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<dbname>
DATASOURCE_USERNAME=<user>
DATASOURCE_PASSWORD=<password>
```

With the `prod` profile Hibernate **validates** the schema only — run your
migrations externally (e.g. Flyway / Liquibase) before starting the app.

---

### Backend (`services/backend/src/main/resources/application.yml` / environment variables)

| Setting | Env Variable | Default | Description |
|---|---|---|---|
| JWT signing secret | `JWT_SECRET` | `dev-only-secret-change-me-in-production-32c` | Secret used to sign and verify JWTs. **Must** be changed to a strong random value in production. |
| Google OAuth2 client ID | `GOOGLE_CLIENT_ID` | `not-configured` | Google OAuth2 client ID for OIDC login. OAuth2 login is disabled until a real value is supplied. |
| Google OAuth2 client secret | `GOOGLE_CLIENT_SECRET` | `not-configured` | Google OAuth2 client secret. See [Google Cloud Console](https://console.cloud.google.com/). |
| GitHub OAuth2 client ID | `GITHUB_CLIENT_ID` | `not-configured` | GitHub OAuth2 client ID for social login. |
| GitHub OAuth2 client secret | `GITHUB_CLIENT_SECRET` | `not-configured` | GitHub OAuth2 client secret. |
| Finnhub API key | `FINNHUB_API_KEY` | *(none)* | API key for Finnhub market data. Supply a real key for meaningful data. |
| Frontend allowed origin (CORS) | `FRONTEND_ORIGIN` | `http://localhost:5173` | Origin the backend permits for CORS and OAuth2 redirects. Set to the production frontend URL in production. |
| Enable synthetic data | `ENABLE_SYNTHETIC_DATA` | `true` | Toggles generation of synthetic/mock market data. |
| Enable real data | `ENABLE_REAL_DATA` | `true` | Toggles fetching of real market data from Finnhub. |
| H2 console | `SPRING_H2_CONSOLE_ENABLED` | `true` | Enables the in-memory H2 web console at `/h2-console`. Disable in production. |
| Active Spring profile | `SPRING_PROFILES_ACTIVE` | *(none)* | Set to `prod` for PostgreSQL. Controls profile-specific behaviour (see [Database configuration](#database-configuration)). |
| Datasource URL | `DATASOURCE_URL` | `jdbc:h2:file:./data/tradelab;AUTO_SERVER=TRUE` | JDBC URL for the database. Override to point at a custom H2 path or a PostgreSQL instance. |
| Datasource username | `DATASOURCE_USERNAME` | *(none — not required for H2)* | Database username. Required when `SPRING_PROFILES_ACTIVE=prod`. |
| Datasource password | `DATASOURCE_PASSWORD` | *(none — not required for H2)* | Database password. Required when `SPRING_PROFILES_ACTIVE=prod`. |

### Docker Compose (`.env` — copy from `.env.example`)

| Setting | Default | Description |
|---|---|---|
| `BACKEND_VERSION` | `latest` | Docker image tag for the backend container. Use a specific version (e.g. `1.0.0`) in production. |
| `FRONTEND_VERSION` | `latest` | Docker image tag for the frontend container. |
| `GOOGLE_CLIENT_ID` | *(none)* | Passed into the backend container for Google OAuth2. |
| `GOOGLE_CLIENT_SECRET` | *(none)* | Passed into the backend container for Google OAuth2. |
| `GITHUB_CLIENT_ID` | *(none)* | Passed into the backend container for GitHub OAuth2. |
| `GITHUB_CLIENT_SECRET` | *(none)* | Passed into the backend container for GitHub OAuth2. |

### Frontend (`services/front-end/.env.development`)

| Setting | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Base URL the frontend uses for all API calls. The OAuth2 redirect must point directly to the backend (not the Vite dev server proxy) because `window.location.href` navigation bypasses Vite. |
