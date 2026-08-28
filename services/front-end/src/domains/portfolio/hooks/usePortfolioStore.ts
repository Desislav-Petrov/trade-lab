import { create } from 'zustand'

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
  setSelectedAccountId: (id) => set({ selectedAccountId: id }),
  toggleSymbolVisibility: (ticker) =>
    set((state) => {
      const hiddenSymbols = new Set(state.hiddenSymbols)
      if (hiddenSymbols.has(ticker)) hiddenSymbols.delete(ticker)
      else hiddenSymbols.add(ticker)
      return { hiddenSymbols }
    }),
  resetSymbolVisibility: () => set({ hiddenSymbols: new Set<string>() }),
}))
