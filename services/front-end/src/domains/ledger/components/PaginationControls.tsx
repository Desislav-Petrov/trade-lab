import { Button } from '@/shared/components/ui/button'

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
      <Button variant="outline" size="sm" onClick={handlePrevious} disabled={currentPage === 0}>
        Previous
      </Button>
      <span className="text-xs text-[var(--color-text-muted)]">
        Page {currentPage + 1} of {totalPages}
      </span>
      <Button
        variant="outline"
        size="sm"
        onClick={handleNext}
        disabled={currentPage === totalPages - 1}
      >
        Next
      </Button>
    </div>
  )
}
