import ReactMarkdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { cn } from '@/shared/lib/utils'
import type { Turn } from '../types/agent'

export interface AssistantMessageProps {
  turn: Turn
}

const markdownComponents: Components = {
  h1: ({ className, ...props }) => (
    <h1
      className={cn('mt-2 text-sm font-semibold text-[var(--color-text-primary)] first:mt-0', className)}
      {...props}
    />
  ),
  h2: ({ className, ...props }) => (
    <h2 className={cn('mt-2 text-xs font-semibold text-[var(--color-text-primary)] first:mt-0', className)} {...props} />
  ),
  h3: ({ className, ...props }) => (
    <h3 className={cn('mt-2 text-xs font-medium text-[var(--color-text-primary)] first:mt-0', className)} {...props} />
  ),
  p: ({ className, ...props }) => (
    <p className={cn('mt-2 whitespace-pre-wrap break-words first:mt-0', className)} {...props} />
  ),
  ul: ({ className, ...props }) => (
    <ul className={cn('mt-2 list-disc space-y-1 pl-4 first:mt-0', className)} {...props} />
  ),
  ol: ({ className, ...props }) => (
    <ol className={cn('mt-2 list-decimal space-y-1 pl-4 first:mt-0', className)} {...props} />
  ),
  li: ({ className, ...props }) => <li className={cn('break-words', className)} {...props} />,
  a: ({ className, ...props }) => (
    <a className={cn('text-[var(--color-accent)] underline underline-offset-2', className)} {...props} />
  ),
  strong: ({ className, ...props }) => (
    <strong className={cn('font-semibold text-[var(--color-text-primary)]', className)} {...props} />
  ),
  code: ({ className, ...props }) => (
    <code
      className={cn(
        'rounded bg-[var(--color-surface)] px-1 py-0.5 font-mono text-[11px] text-[var(--color-text-primary)]',
        className,
      )}
      {...props}
    />
  ),
  pre: ({ className, ...props }) => (
    <pre
      className={cn(
        'mt-2 overflow-x-auto rounded bg-[var(--color-surface)] p-2 font-mono text-[11px] text-[var(--color-text-primary)] first:mt-0',
        className,
      )}
      {...props}
    />
  ),
  table: ({ className, ...props }) => (
    <div className="mt-2 overflow-x-auto first:mt-0">
      <table className={cn('w-full border-collapse text-left text-[11px]', className)} {...props} />
    </div>
  ),
  thead: ({ className, ...props }) => (
    <thead className={cn('border-b border-[var(--color-border)]', className)} {...props} />
  ),
  tbody: ({ className, ...props }) => <tbody className={cn('divide-y divide-[var(--color-border)]', className)} {...props} />,
  tr: ({ className, ...props }) => <tr className={cn('align-top', className)} {...props} />,
  th: ({ className, ...props }) => (
    <th
      className={cn('px-2 py-1 font-semibold text-[var(--color-text-primary)]', className)}
      {...props}
    />
  ),
  td: ({ className, ...props }) => (
    <td className={cn('px-2 py-1 text-[var(--color-text-secondary)]', className)} {...props} />
  ),
}

export function AssistantMessage({ turn }: AssistantMessageProps) {
  const isUser = turn.role === 'user'
  const text = turn.text || (turn.isStreaming ? 'Streaming reply…' : '')

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
        {isUser ? (
          <p className="whitespace-pre-wrap break-words">{text}</p>
        ) : (
          <ReactMarkdown components={markdownComponents} remarkPlugins={[remarkGfm]}>
            {text}
          </ReactMarkdown>
        )}
        {turn.isStreaming && (
          <span className="mt-1 block text-[10px] text-[var(--color-text-muted)]">Streaming…</span>
        )}
      </div>
    </div>
  )
}
