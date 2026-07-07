import { create } from 'zustand'

interface StockTradingState {
  selectedAccountId: string | null
  setSelectedAccountId: (id: string) => void
  clearSelectedAccountId: () => void
}

export const useStockTradingStore = create<StockTradingState>((set) => ({
  selectedAccountId: null,
  setSelectedAccountId: (id) => set({ selectedAccountId: id }),
  clearSelectedAccountId: () => set({ selectedAccountId: null }),
}))
