import { create } from 'zustand'

interface SelectedAccountState {
  selectedAccountId: string | null
  setSelectedAccountId: (accountId: string) => void
  clearSelectedAccountId: () => void
}

export const useSelectedAccountStore = create<SelectedAccountState>((set) => ({
  selectedAccountId: null,
  setSelectedAccountId: (accountId) => set({ selectedAccountId: accountId }),
  clearSelectedAccountId: () => set({ selectedAccountId: null }),
}))
