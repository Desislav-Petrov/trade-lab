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

## Spring Boot Actuator

The backend exposes operational endpoints via Spring Boot Actuator at the `/actuator` base path.

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
