interface PaginationControlsProps {
  currentPage: number
  totalPages: number
  onPageChange: (page: number) => void
}

export function PaginationControls({
  currentPage,
  totalPages,
  onPageChange,
}: PaginationControlsProps) {
  if (totalPages === 0) {
    return null
  }

  function handlePrevious() {
    onPageChange(currentPage - 1)
  }

  function handleNext() {
    onPageChange(currentPage + 1)
  }

  return (
    <div className="flex items-center gap-3">
      <button
        type="button"
        onClick={handlePrevious}
        disabled={currentPage === 0}
        className="rounded border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-1 text-xs text-[var(--color-text-primary)] hover:border-[var(--color-text-muted)] disabled:cursor-not-allowed disabled:opacity-40"
      >
        Previous
      </button>
      <span className="text-xs text-[var(--color-text-muted)]">
        Page {currentPage + 1} of {totalPages}
      </span>
      <button
        type="button"
        onClick={handleNext}
        disabled={currentPage === totalPages - 1}
        className="rounded border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-1 text-xs text-[var(--color-text-primary)] hover:border-[var(--color-text-muted)] disabled:cursor-not-allowed disabled:opacity-40"
      >
        Next
      </button>
    </div>
  )
}
