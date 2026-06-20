export function Topbar() {
  return (
    <header
      aria-label="Top bar"
      className="flex h-12 shrink-0 items-center justify-between border-b border-[var(--color-border)] bg-[var(--color-surface)] px-4"
    >
      <span className="text-xs font-medium tracking-widest text-[var(--color-accent)]">
        TRADE-LAB
      </span>
      <div aria-label="User area" className="text-xs text-[var(--color-text-muted)]">
        —
      </div>
    </header>
  )
}
