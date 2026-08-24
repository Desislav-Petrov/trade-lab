# 2026-08-24 — Introduce Shadcn/ui as the design system

## Status
Accepted

## Context

The frontend was built with Tailwind CSS v4 for styling but had no component
library. All shared UI primitives (buttons, inputs, modals, tables, etc.) would
need to be hand-rolled for every new feature, without consistent accessibility
patterns, keyboard navigation, or ARIA attributes.

As the number of screens grows (trading, portfolio, accounts, profile), the
cost of duplicated, inconsistent UI primitives becomes prohibitive. A design
system provides a shared vocabulary and cuts boilerplate significantly.

## Decision

Adopt **Shadcn/ui** as the design system.

Key reasons:

1. **Tailwind-native** — Shadcn/ui is built on Tailwind CSS and uses no
   CSS-in-JS runtime. It integrates cleanly with the existing Tailwind v4 setup.

2. **Source ownership** — Shadcn/ui copies component source files directly into
   the repo under `src/shared/components/ui/`. There is no `shadcn` package in
   `node_modules`. Components are fully owned and can be customised without
   forking an upstream library.

3. **Radix UI primitives** — All interactive components (Dialog, Select, Tabs,
   Toast, Tooltip) are built on Radix UI, which provides accessible, headless
   primitives: correct ARIA roles, keyboard navigation, and focus management
   out of the box.

4. **React 19 compatible** — Verified compatible with the React 19 version
   used in this project.

5. **Trading platform aesthetics** — Shadcn's minimal, neutral design pairs
   naturally with the existing dark terminal palette (`--color-bg: #0d0d0d`,
   `--color-accent: #00ff88`). CSS variable tokens are mapped to trade-lab's
   palette in `globals.css`.

6. **No heavy bundle cost** — Runtime additions are only Radix UI primitives
   (tree-shakeable), `class-variance-authority`, `clsx`, `tailwind-merge`, and
   `lucide-react`. No CSS-in-JS runtime, no emotion, no MUI theme provider.

## Alternatives considered

| Option | Reason rejected |
|---|---|
| Material UI | Heavy CSS-in-JS runtime, conflicts with Tailwind, imposes Google's design language |
| Chakra UI | Similar CSS-in-JS overhead, React 19 support lagged |
| Ant Design | Opinionated enterprise look, large bundle, less compatible with custom palettes |
| Custom only | Significant accessibility and consistency risk at scale |

## Consequences

### Added dependencies (runtime)
- `@radix-ui/react-*` — accessible primitive components
- `class-variance-authority` — variant-based class composition (`cva`)
- `clsx` — conditional class concatenation
- `tailwind-merge` — Tailwind class conflict resolution
- `lucide-react` — icon library (used by Shadcn components)

### Added files
- `services/front-end/components.json` — Shadcn config (aliases, style, icon library)
- `services/front-end/src/shared/lib/utils.ts` — `cn()` utility (`clsx` + `twMerge`)
- `services/front-end/src/shared/components/ui/` — UI primitives:
  `button`, `card`, `input`, `label`, `badge`, `tabs`, `dialog`, `select`,
  `table`, `toast`, `skeleton`, `alert`, `separator`, `tooltip`
- `services/front-end/src/shared/components/Toaster.tsx` — global toast renderer
- `services/front-end/src/shared/hooks/useToast.ts` — programmatic toast hook

### Modified files
- `globals.css` — Shadcn CSS variable layer added (maps trade-lab palette to
  Shadcn semantic tokens)
- `tsconfig.json` / `vite.config.ts` — `@/*` path alias added for Shadcn imports
- `App.tsx` — `<Toaster />` added at root for global notifications
- `standards/frontend.md` — design system section added
- `package.json` — Shadcn runtime deps added

### Rules
- All new UI primitives must use Shadcn components from `src/shared/components/ui/`
  before creating custom ones.
- The `cn()` utility from `@/shared/lib/utils` must be used for all class
  composition — never concatenate Tailwind strings manually.
- Shadcn CSS variable tokens (`hsl(var(--xxx))`) are used inside
  `src/shared/components/ui/` only. Domain components continue to use
  the trade-lab palette tokens (`var(--color-*)`) via Tailwind classes.
- No new Radix UI primitives are to be imported directly in domain components —
  always use the Shadcn wrapper from `ui/`.
