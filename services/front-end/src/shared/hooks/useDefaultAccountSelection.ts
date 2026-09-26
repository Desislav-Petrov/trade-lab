import { useEffect } from 'react'
import { useActiveAccounts } from '../../domains/ledger/hooks/useLedger'
import { useSelectedAccountStore } from './useSelectedAccountStore'

/**
 * Seeds the shared selected-account store with the first active account for the
 * authenticated session, independent of which page is currently mounted.
 *
 * Without this, the default account was only resolved inside PortfolioPage's
 * effect, so any globally-mounted consumer (e.g. the AI assistant) saw a null
 * account until the user first visited the Portfolio page. See
 * decisions/2026-09-26-global-default-account-selection.md.
 */
export function useDefaultAccountSelection(): void {
  const { data } = useActiveAccounts()
  const selectedAccountId = useSelectedAccountStore((state) => state.selectedAccountId)
  const setSelectedAccountId = useSelectedAccountStore((state) => state.setSelectedAccountId)

  useEffect(() => {
    const accounts = data?.accounts ?? []
    if (selectedAccountId === null && accounts.length > 0) {
      setSelectedAccountId(accounts[0].id)
    }
  }, [data, selectedAccountId, setSelectedAccountId])
}
