import { Button } from '@/shared/components/ui/button'

interface RemoveTickerBarProps {
  selectedCount: number
  onRemove: () => void
  isLoading: boolean
}

export function RemoveTickerBar({ selectedCount, onRemove, isLoading }: RemoveTickerBarProps) {
  const isDisabled = selectedCount === 0 || isLoading

  return (
    <div className="flex items-center gap-3">
      <Button
        type="button"
        variant="destructive"
        size="sm"
        onClick={onRemove}
        disabled={isDisabled}
      >
        {isLoading ? 'Removing…' : `Remove selected (${selectedCount})`}
      </Button>
    </div>
  )
}
