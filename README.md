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
