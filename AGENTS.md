# AGENTS.md

## What this project is
A paper trading platform where users can simulate trading equities 
without real money. AI agents assist with both code generation and the trading experience itself. The platform has both backend and frontend

## Repo structure
- `domain/model`       — Entity and concept documentation
- `domain/flows`       — Business flow documentation
- `domain/usecases`    — Use case documentation
- `standards/*`        — Architecture, backend, and frontend standards
- `services/back-end`  — Backend Spring Boot application
- `services/front-end` — Frontend React application
- `tasks/`             — Decomposed implementation task lists

## Domain
All domain knowledge lives in `domain/`. Before implementing anything,
read the relevant entity and flow docs. Never invent behaviour that isn't
described there.

## Agents
- **design-agent** — Refines and gets requirements and iterates on the domain/flow/use case taking into account if it's a new functionality or an extension
- **decomposer-agent** - Breaks down a usecase into tasks across the different layers so that an implementer agent can execute them

## Decision log
All non-obvious decisions are logged in `decisions/`. Read it before making
architectural choices — the answer may already exist.

## Rules
- Never modify domain docs without a corresponding decision log entry
- Always read the relevant standards doc before writing code
- One task, one layer — don't implement across layers in a single pass
- If something is unclear, surface it — don't assume
- Whenever you modify any file, check whether related docs (domain, standards, agent instructions) need updating and do it in the same pass