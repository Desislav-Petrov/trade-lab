# Frontend Standards

## Tech Stack

| Concern | Choice |
|---|---|
| Language | TypeScript |
| Framework | React 19 |
| Build tool | Vite |
| Server state | TanStack Query v5 |
| Client state | Zustand |
| HTTP client | Axios |
| Testing | Vitest + React Testing Library |
| Package manager | npm |

---

## Project Structure

Frontend code lives in `services/front-end/`. The domain structure mirrors the
backend domain taxonomy — one folder per domain under `src/domains/`.

```
services/front-end/
  src/
    domains/
      user/
        api/          # Axios calls matching the BE REST contract (CLI layer)
        components/   # Domain-specific React components (COMP layer)
        hooks/        # TanStack Query hooks and Zustand store slices (STATE layer)
        pages/        # Full page compositions (SCREEN layer)
        types/        # TypeScript interfaces for this domain
      ledger/         # same structure
      marketdata/     # same structure
      stocktrading/   # same structure
    shared/
      api/            # Axios instance, base config, interceptors
      components/     # Shared UI components
      hooks/          # Shared hooks
      types/          # Shared TypeScript types
    app/
      App.tsx         # Root component
      router.tsx      # React Router configuration
      main.tsx        # Entry point
```

One component per file. One test file per source file.

---

## Layer Responsibilities

These IDs match those used by the decomposer agent.

| ID | Sub-folder | What belongs here |
|----|------------|-------------------|
| CLI | `api/` | Axios calls for this domain — one file per resource, typed request and response |
| STATE | `hooks/` | TanStack Query hooks (`useQuery`, `useMutation`), Zustand store slices |
| COMP | `components/` | Individual React components — forms, lists, inputs, displays |
| SCREEN | `pages/` | Full page compositions — assembles components, handles routing |

**Rules:**
- No API calls directly in components — always go through a hook in `{domain}/hooks/`.
- No server state in Zustand — TanStack Query owns all data fetched from the backend.
- No prop drilling beyond two levels — lift state to a hook.
- Pages assemble components; they do not contain business logic.

---

## API Client Conventions

- One shared Axios instance configured in `shared/api/` with base URL and
  interceptors.
- Domain `api/` modules import from the shared instance — never create a new
  Axios instance per domain.
- All API calls are explicitly typed: request and response interfaces defined
  in `{domain}/types/`.
- Function naming: verb + noun — `createUser`, `fetchUsers`, `loginUser`.
- TanStack Query cache keys defined as constants in `{domain}/api/`.

---

## State Management

- **Server state (TanStack Query):** all data fetched from the backend. Never
  duplicate backend data in Zustand.
- **Client state (Zustand):** UI-only state — modal open/closed, selected
  items, ephemeral form state.
- One Zustand store slice per domain, defined in `{domain}/hooks/`.
- TanStack Query mutations invalidate the relevant query keys on success to
  keep the cache consistent.

---

## Component Conventions

- Functional components only — no class components.
- Each component has an explicit `[ComponentName]Props` TypeScript interface.
- Event handler props are prefixed `on` (e.g. `onSubmit`); handler
  implementations inside the component are prefixed `handle` (e.g.
  `handleSubmit`).
- No API calls or state store access directly inside a component — use a hook.

---

## Testing

- Vitest + React Testing Library.
- Test files co-located with source: `ComponentName.test.tsx`.
- Hook tests use `renderHook` from RTL.
- API modules mocked with `vi.mock`.
- Test naming: `ComponentName - scenario - expected outcome`.
- No snapshot tests.

### Coverage expectations
- All hooks must have tests covering the happy path and every defined error
  case from the flow docs.
- All page components must have tests covering the success render and each
  error/empty state.

---

## Build

| Task | Purpose |
|---|---|
| `npm run dev` | Start the development server |
| `npm run build` | Production build |
| `npm run test` | Run all tests |

---

## Coding Conventions

- No `any` — always type explicitly. Use `unknown` for untyped external data
  and narrow at the boundary.
- Props interfaces named `[ComponentName]Props`.
- All timestamps received from the backend are UTC ISO 8601 — convert to
  local timezone only at the display layer.
- Import order: external libraries, then `shared/`, then same-domain modules.
  No imports from another domain's folder.
