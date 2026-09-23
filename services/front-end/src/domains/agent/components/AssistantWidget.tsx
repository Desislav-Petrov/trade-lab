import { Button } from '../../../shared/components/ui/button'
import { cn } from '../../../shared/lib/utils'
import { useAgentStore } from '../hooks/useAgentStore'

export interface AssistantWidgetProps {
  className?: string
}

export function AssistantWidget({ className }: AssistantWidgetProps) {
  const isOpen = useAgentStore((state) => state.isOpen)
  const toggle = useAgentStore((state) => state.toggle)

  function handleClick() {
    toggle()
  }

  return (
    <Button
      type="button"
      onClick={handleClick}
      aria-expanded={isOpen}
      aria-haspopup="dialog"
      className={cn('fixed right-6 bottom-6 z-40 shadow-lg', className)}
    >
      Trade Lab AI Assistant
    </Button>
  )
}
