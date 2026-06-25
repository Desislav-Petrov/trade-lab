interface RemoveTickerBarProps {
  selectedCount: number
  onRemove: () => void
  isLoading: boolean
}

export function RemoveTickerBar({ selectedCount, onRemove, isLoading }: RemoveTickerBarProps) {
  const isDisabled = selectedCount === 0 || isLoading

  return (
    <div className="flex items-center gap-3">
      <button
        type="button"
        onClick={onRemove}
        disabled={isDisabled}
        className="rounded border border-[var(--color-danger)] px-3 py-1 text-xs text-[var(--color-danger)] transition-opacity disabled:cursor-not-allowed disabled:opacity-40 hover:bg-[var(--color-danger)] hover:text-[var(--color-bg)]"
      >
        {isLoading ? 'Removing…' : `Remove selected (${selectedCount})`}
      </button>
    </div>
  )
}
