import { create } from 'zustand'

interface PortfolioState {
  selectedAccountId: string | null
  setSelectedAccountId: (id: string) => void
}

export const usePortfolioStore = create<PortfolioState>((set) => ({
  selectedAccountId: null,
  setSelectedAccountId: (id) => set({ selectedAccountId: id }),
}))
