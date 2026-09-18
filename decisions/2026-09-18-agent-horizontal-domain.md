# Decision: Agent Domain — Horizontal Classification, Google ADK Adoption, and Portfolio Evaluation Scaffold

**Date:** 2026-09-18
**Status:** accepted

## Context

Issue #188 introduces a new `agent` domain responsible for the platform's agentic
integrations. The initial deliverable is a scaffold only — there is no domain
behaviour yet. The endpoint returns a placeholder response and the service class
is empty. Three non-obvious choices had to be settled before any implementation
begins, and they are recorded here so downstream decomposition and engineering
work has an authoritative reference.

### 1. `agent` is a horizontal domain

The architecture standard classifies every domain as either a **horizontal**
(foundational, serves the entire platform, may be depended upon by any vertical)
or a **vertical** (a self-contained product experience). Agentic integrations are
not a standalone product — they are a cross-cutting capability that any vertical
(Stock Trading today; Crypto/Options in future) will consume to obtain agentic
assistance. This mirrors the role of Market Data.

**Decision:** `agent` is a **horizontal** domain. Per the dependency rules, it may
depend on other horizontals (e.g. Portfolio, Ledger, Market Data) via their `api/`
interfaces, and verticals may depend on it. Horizontals must never depend on
verticals — this holds for `agent`.

### 2. Google ADK for Java as the agentic-integration library

The agent domain needs a framework to build and orchestrate agentic workflows.
Google's Agent Development Kit (ADK) for Java is adopted as that framework.

**Decision:** Add **Google ADK for Java** as a backend dependency. The exact
version is pinned by the backend-engineer at implementation time to the latest
stable release available then — it is intentionally not hard-coded in this
decision record to avoid documenting a version that drifts. The dependency is
scoped to the `agent` domain's build configuration.

### 3. `evaluatePortfolio` scaffold — placeholder response, empty service

The first endpoint is `POST /api/v1/agent/evaluatePortfolio`. It exists to
establish the domain's REST surface and wiring; it performs no agentic work yet.

**Decision:**

- **Endpoint:** `POST /api/v1/agent/evaluatePortfolio`.
- **Request body:** `{ "accountId": "<uuid>" }` — `accountId` only.
- **Authentication:** `userId` is resolved server-side from the JWT, never taken
  from the request body.
- **Response:** `200 OK` with a placeholder DTO carrying the echoed `accountId`
  and a `status` field set to `NOT_IMPLEMENTED`.
- **Service:** an empty `AgentService` is scaffolded. The controller
  (`AgentApiDelegateImpl`) calls it, but the service has no behaviour yet.

No `domain/model` or `domain/flow` docs are created for `agent` at this stage.
Manufacturing behaviour docs for an empty scaffold would document work the code
does not perform. Domain docs will be authored when real behaviour lands, each
accompanied by its own decision-log entry.

## Decision

1. `agent` is a **horizontal** domain, following the standard domain package
   structure.
2. **Google ADK for Java** is the agentic-integration library; the version is
   pinned by the engineer to the latest stable release at implementation time.
3. `POST /api/v1/agent/evaluatePortfolio` is scaffolded: `accountId` in the body,
   `userId` from the JWT, `200 OK` placeholder response (`accountId` +
   `status: NOT_IMPLEMENTED`), calling an empty `AgentService`.
4. No `domain/model` or `domain/flow` docs are written for the scaffold; the
   domain's footprint is captured in `standards/architecture.md`, this decision,
   and the OpenAPI contract.

## Consequences

- `standards/architecture.md` is updated: `agent` is added to the Horizontals
  table and to both the backend and frontend domain structure trees.
- A new contract `services/contract/agent-openapi.yaml` must be authored by the
  backend-engineer, defining `POST /api/v1/agent/evaluatePortfolio` with the
  request/response shapes described above. Request/response DTOs and the
  `AgentApiDelegate` are generated from it — never hand-written.
- The backend-engineer adds the Google ADK for Java dependency to the build
  configuration and pins the version.
- An empty `AgentService` and an `AgentApiDelegateImpl` controller are created in
  the `agent` domain package. The controller resolves `userId` from the JWT and
  returns the placeholder response.
- When the `agent` domain is later extracted into a standalone service, its
  cross-domain reads (Portfolio, Ledger, Market Data `api/` interfaces) and any
  future event subscriptions follow the existing extraction pattern — no domain
  logic rewrite required.
