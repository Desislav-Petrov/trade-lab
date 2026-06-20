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
(JDBC URL: `jdbc:h2:mem:tradelab`, no credentials required).

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
- npm 10 or above

### Install dependencies

```bash
cd services/front-end
npm install
```

### Start

```bash
npm run dev
```

The application starts on `http://localhost:5173`.

The dev server proxies all `/api` requests to the backend at `http://localhost:8080`. Start the backend first.

### Other npm tasks

| Task | Purpose |
|---|---|
| `npm run build` | Production build (output: `dist/`) |
| `npm run test` | Run all tests |
| `npm run lint` | Run ESLint |
| `npm run format` | Format source files with Prettier |
| `npm run format:check` | Check formatting without writing |
