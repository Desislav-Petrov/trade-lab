import { create } from 'zustand'
import { useSelectedAccountStore } from '../../../shared/hooks/useSelectedAccountStore'

interface PortfolioState {
  selectedAccountId: string | null
  hiddenSymbols: Set<string>
  setSelectedAccountId: (id: string) => void
  toggleSymbolVisibility: (ticker: string) => void
  resetSymbolVisibility: () => void
}

export const usePortfolioStore = create<PortfolioState>((set) => ({
  selectedAccountId: null,
  hiddenSymbols: new Set<string>(),
  setSelectedAccountId: (id) => {
    useSelectedAccountStore.getState().setSelectedAccountId(id)
    set({ selectedAccountId: id })
  },
  toggleSymbolVisibility: (ticker) =>
    set((state) => {
      const hiddenSymbols = new Set(state.hiddenSymbols)
      if (hiddenSymbols.has(ticker)) hiddenSymbols.delete(ticker)
      else hiddenSymbols.add(ticker)
      return { hiddenSymbols }
    }),
  resetSymbolVisibility: () => set({ hiddenSymbols: new Set<string>() }),
}))

usePortfolioStore.subscribe((state, previousState) => {
  if (state.selectedAccountId === previousState.selectedAccountId) return
  if (useSelectedAccountStore.getState().selectedAccountId === state.selectedAccountId) return

  useSelectedAccountStore.setState({ selectedAccountId: state.selectedAccountId })
})

useSelectedAccountStore.subscribe((state, previousState) => {
  if (state.selectedAccountId === previousState.selectedAccountId) return
  if (usePortfolioStore.getState().selectedAccountId === state.selectedAccountId) return

  usePortfolioStore.setState({ selectedAccountId: state.selectedAccountId })
})
