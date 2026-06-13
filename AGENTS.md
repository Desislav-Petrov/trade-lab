# AGENTS.md

## What this project is
A paper trading platform where users can simulate trading equities 
without real money. AI agents assist with both code generation and the trading experience itself. The platform has both backend and frontend

## Repo structure
- `domain/models` — The domain models for the platfrom
- `domain/flows` — The domain business flows in the platform
- `domain/usecases` - The usecases that an actor needs to fulfil through the platform

## Domain
All domain knowledge lives in `domain/`. Before implementing anything,
read the relevant entity and flow docs. Never invent behaviour that isn't
described there.

## Agents
- **clarifier** — turns rough ideas into structured use case docs
- **domain-mapper** — maps a use case to entities and flows
- **task-decomposer** — breaks a use case into per-layer tasks
- **implementer** — executes a single task against standards and domain
- **reviewer** — validates output against architecture and original spec

## Decision log
All non-obvious decisions are logged in `decisions/`. Read it before making
architectural choices — the answer may already exist.

## Rules
- Never modify domain docs without a corresponding decision log entry
- Always read the relevant standards doc before writing code
- One task, one layer — don't implement across layers in a single pass
- If something is unclear, surface it — don't assume