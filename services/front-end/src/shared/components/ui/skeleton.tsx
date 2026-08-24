import { cn } from '@/shared/lib/utils'

/**
 * Skeleton — pulsing placeholder for loading states.
 * Usage: <Skeleton className="h-4 w-32" />
 */
function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('animate-pulse rounded bg-[hsl(var(--muted))]', className)}
      {...props}
    />
  )
}

export { Skeleton }
