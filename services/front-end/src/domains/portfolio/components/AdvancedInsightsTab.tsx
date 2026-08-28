import { useEffect } from 'react'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'
import { Skeleton } from '@/shared/components/ui/skeleton'
import { useFillHistory } from '../hooks/useFillHistory'
import { usePortfolioStore } from '../hooks/usePortfolioStore'
import { FillHistoryChart } from './FillHistoryChart'

export interface AdvancedInsightsTabProps {
  accountId: string | null
}

export function AdvancedInsightsTab({ accountId }: AdvancedInsightsTabProps) {
  const { fills, isLoading, isError } = useFillHistory(accountId)
  const hiddenSymbols = usePortfolioStore((s) => s.hiddenSymbols)
  const toggleSymbolVisibility = usePortfolioStore((s) => s.toggleSymbolVisibility)
  const resetSymbolVisibility = usePortfolioStore((s) => s.resetSymbolVisibility)

  useEffect(() => {
    resetSymbolVisibility()
  }, [accountId, resetSymbolVisibility])

  if (isLoading) {
    return (
      <div data-testid="advanced-insights-loading" className="flex flex-col gap-2">
        <Skeleton className="h-80 w-full" />
        <Skeleton className="h-8 w-48" />
      </div>
    )
  }

  if (isError) {
    return (
      <Alert variant="destructive" role="alert">
        <AlertDescription>Could not load price history. Please try again.</AlertDescription>
      </Alert>
    )
  }

  return (
    <FillHistoryChart
      fills={fills}
      hiddenSymbols={hiddenSymbols}
      onToggleSymbol={toggleSymbolVisibility}
    />
  )
}
