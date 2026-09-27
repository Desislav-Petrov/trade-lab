# 2026-09-27 — Render AI assistant replies as GitHub-flavoured markdown

## Status
Accepted

## Context

The AI assistant streams markdown-formatted replies that can include headings,
emphasis, lists, inline code, and GitHub-flavoured markdown (GFM) tables such
as portfolio holdings breakdowns. The `chat-with-ai-assistant` frontend use case
currently renders every turn as plain text, so assistant responses show literal
markdown markers (`###`, `**`, `|`) instead of formatted content.

The assistant reply surface is also an XSS boundary. Assistant text may contain
embedded HTML-like strings, but the frontend must not turn that text into live
HTML elements or execute browser event handlers.

## Decision

Use `react-markdown` with the `remark-gfm` plugin to render **assistant turns
only** in `services/front-end/src/domains/agent/components/AssistantMessage.tsx`.

User turns remain plain text echoes of the user's own input. Assistant replies
are rendered as markdown both when streamed token-by-token and when shown via
the buffered fallback reply. Raw HTML rendering remains disabled — no
`rehype-raw`, no `dangerouslySetInnerHTML`, and no other raw-HTML plugin is
allowed in this path.

## Alternatives considered

| Option | Reason rejected |
|---|---|
| Keep rendering assistant replies as plain text | Leaves the feature visibly broken for headings, emphasis, lists, and holdings tables that the assistant already emits. |
| Hand-rolled markdown parser/rendering logic | Unnecessary maintenance burden and higher correctness risk for standard markdown/GFM features already handled by a maintained library. |
| `react-markdown` with `rehype-raw` | Rejecting this preserves the XSS boundary; embedded HTML in assistant replies must stay escaped/inert rather than become live DOM. |

## Consequences

### Added dependencies (runtime)
- `react-markdown`
- `remark-gfm`

### Modified files
- `services/front-end/package.json` — assistant markdown rendering dependencies
- `services/front-end/pnpm-lock.yaml` — lockfile update after `pnpm install`
- `services/front-end/src/domains/agent/components/AssistantMessage.tsx` — assistant-only markdown rendering with compact trade-lab styling
- `services/front-end/src/domains/agent/components/AssistantMessage.test.tsx` — markdown, HTML-escaping, and streaming-placeholder coverage
- `standards/frontend.md` — sanctioned assistant markdown rendering note

### Rules
- Render markdown for assistant turns only; user turns remain plain text.
- Use `react-markdown` with `remark-gfm` for assistant replies in this frontend path.
- Do not enable raw HTML rendering (`rehype-raw` or equivalent) for assistant replies.
- Preserve the existing streamed placeholder and streaming indicator behaviour for empty in-flight replies.
