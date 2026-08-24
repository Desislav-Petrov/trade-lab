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
| Styling | Tailwind CSS v4 |
| Design system | Shadcn/ui (source-owned, Radix UI primitives) |
| Icons | Lucide React |
| Testing | Vitest + React Testing Library |
| Package manager | pnpm |

---

## Design System — Shadcn/ui

See `decisions/2026-08-24-design-system-shadcn.md` for the full rationale.

Shadcn/ui components live in `src/shared/components/ui/`. They are **source
files owned by this repo**, not a node_modules package. They can be modified
freely.

### Available components

| Component | File | Use case |
|---|---|---|
| `Button` | `ui/button.tsx` | All clickable actions |
| `Input` | `ui/input.tsx` | Text, number inputs |
| `Label` | `ui/label.tsx` | Form field labels |
| `Card` / `CardHeader` / `CardContent` / `CardFooter` | `ui/card.tsx` | Summary panels |
| `Badge` | `ui/badge.tsx` | Status tags (order status, asset class) |
| `Tabs` / `TabsList` / `TabsTrigger` / `TabsContent` | `ui/tabs.tsx` | Tabbed views |
| `Dialog` / `DialogContent` / `DialogHeader` | `ui/dialog.tsx` | Confirmation modals |
| `Select` / `SelectTrigger` / `SelectContent` / `SelectItem` | `ui/select.tsx` | Dropdowns |
| `Table` / `TableHeader` / `TableBody` / `TableRow` / `TableCell` | `ui/table.tsx` | Data tables |
| `Toast` | `ui/toast.tsx` + `Toaster.tsx` | Notifications |
| `Skeleton` | `ui/skeleton.tsx` | Loading placeholders |
| `Alert` | `ui/alert.tsx` | Inline error/warning/success messages |
| `Separator` | `ui/separator.tsx` | Layout dividers |
| `Tooltip` / `TooltipContent` | `ui/tooltip.tsx` | Hover explanations |

### `cn()` utility

All class composition must use `cn()` from `@/shared/lib/utils`:

```ts
import { cn } from '@/shared/lib/utils'

<div className={cn('base-class', condition && 'conditional-class', className)} />
```

Never manually concatenate Tailwind strings — use `cn()` to avoid conflicts.

### CSS variable convention

- Inside `src/shared/components/ui/`: use Shadcn semantic tokens
  (`hsl(var(--primary))`, `hsl(var(--border))`, etc.)
- In domain components: use trade-lab palette tokens
  (`var(--color-accent)`, `var(--color-border)`, etc.) via Tailwind utility
  classes (`text-[var(--color-accent)]`)

### Badge variants for trading

The `Badge` component extends Shadcn defaults with:
- `success` — green, for filled orders / positive P&L
- `warning` — amber, for pending states
- `danger` — red, for cancelled / negative states

### Toast

Use the `useToast` hook from `@/shared/hooks/useToast` for programmatic toasts:

```ts
import { useToast } from '@/shared/hooks/useToast'

const { toast } = useToast()
toast({ title: 'Order placed', variant: 'success' })  // or 'destructive'
```

`<Toaster />` is mounted once in `App.tsx`.

### Rules

- All new UI primitives must use Shadcn components from `src/shared/components/ui/`
  before creating custom ones.
- No Radix UI primitives imported directly in domain components — always use
  the Shadcn wrapper from `ui/`.
- No new component library may be added without a decision log entry.

---

## Backend Communication

- The frontend communicates with the backend exclusively via REST (JSON over HTTP).
- The API contract is defined in OpenAPI 3.0 and lives in `services/contract/trade-lab-openapi.yaml`. This is the single source of truth for all endpoint URLs, HTTP methods, request/response shapes, and error contracts.
- The CLI layer (`{domain}/api/`) must be derived from this contract — no endpoint URLs, HTTP methods, or payload shapes are to be invented.
- The shared Axios instance in `shared/api/` is the only HTTP client. Domain `api/` modules import from it; no new Axios instances are created per domain.

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
      portfolio/      # same structure 
    shared/
      api/            # Axios instance, base config, interceptors
      components/     # Shared UI components
        ui/           # Shadcn/ui primitive components (source-owned)
      hooks/          # Shared hooks (incl. useToast)
      lib/            # Utilities (cn, etc.)
      types/          # Shared TypeScript types
    app/
      App.tsx         # Root component
      router.tsx      # TanStack Router route tree configuration
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
| `pnpm run dev` | Start the development server |
| `pnpm run build` | Production build |
| `pnpm run test` | Run all tests |
| `pnpm run lint` | Run Oxlint on frontend source |
| `pnpm run format` | Format frontend source with Oxfmt |
| `pnpm run format:check` | Check frontend formatting with Oxfmt |

---

## Coding Conventions

- No `any` — always type explicitly. Use `unknown` for untyped external data
  and narrow at the boundary.
- Props interfaces named `[ComponentName]Props`.
- All timestamps received from the backend are UTC ISO 8601 — convert to
  local timezone only at the display layer.
- Import order: external libraries, then `shared/`, then same-domain modules.
  No imports from another domain's folder.
- Use `cn()` from `@/shared/lib/utils` for all Tailwind class composition.
