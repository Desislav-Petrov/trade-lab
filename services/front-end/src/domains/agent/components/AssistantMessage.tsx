import { cn } from '../../../shared/lib/utils'
import type { Turn } from '../types/agent'

export interface AssistantMessageProps {
  turn: Turn
}

export function AssistantMessage({ turn }: AssistantMessageProps) {
  const isUser = turn.role === 'user'

  return (
    <div className={cn('flex w-full', isUser ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[85%] rounded px-3 py-2 text-xs',
          isUser
            ? 'bg-[var(--color-accent)] text-[var(--color-bg)]'
            : 'border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)]',
        )}
      >
        <p>{turn.text || (turn.isStreaming ? 'Streaming reply…' : '')}</p>
        {turn.isStreaming && (
          <span className="mt-1 block text-[10px] text-[var(--color-text-muted)]">Streaming…</span>
        )}
      </div>
    </div>
  )
}
