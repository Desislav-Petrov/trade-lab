import { create } from 'zustand'
import { useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { placeOrder } from '../api/ordersApi'
import type { PlaceOrderResponse } from '../api/ordersApi'
import { fetchIndicativePrice } from '../../marketdata/api/marketDataApi'
import { usePortfolioStore } from '../../portfolio/hooks/usePortfolioStore'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import { PORTFOLIO_HOLDINGS_KEY } from '../../portfolio/api/portfolioApi'
import { ACCOUNTS_QUERY_KEY } from '../../ledger/api/accountApi'

interface SellPanelState {
  isOpen: boolean
  ticker: string | null
  maxQuantity: number | null
  priceSnapshot: number | null
  idempotencyKey: string | null
  quantity: string
  validationError: string | null
  isFetchingPrice: boolean
  priceError: string | null
  isSubmitting: boolean
  submitError: string | null
  result: PlaceOrderResponse | null
}

interface SellPanelStoreState extends SellPanelState {
  openSellPanel: (ticker: string, maxQuantity: number) => Promise<void>
  closeSellPanel: () => void
  setQuantity: (value: string) => void
  _confirmSell: (onFilled: () => Promise<void>) => Promise<void>
}

const INITIAL_STATE: SellPanelState = {
  isOpen: false,
  ticker: null,
  maxQuantity: null,
  priceSnapshot: null,
  idempotencyKey: null,
  quantity: '',
  validationError: null,
  isFetchingPrice: false,
  priceError: null,
  isSubmitting: false,
  submitError: null,
  result: null,
}

export const useSellPanelStore = create<SellPanelStoreState>((set, get) => ({
  ...INITIAL_STATE,

  openSellPanel: async (ticker: string, maxQuantity: number): Promise<void> => {
    set({ isFetchingPrice: true })
    try {
      const response = await fetchIndicativePrice(ticker)
      set({
        priceSnapshot: response.indicativePrice,
        idempotencyKey: crypto.randomUUID(),
        ticker,
        maxQuantity,
        isOpen: true,
        isFetchingPrice: false,
        priceError: null,
      })
    } catch {
      set({ priceError: 'Could not fetch indicative price.', isFetchingPrice: false })
    }
  },

  closeSellPanel: (): void => {
    set(INITIAL_STATE)
  },

  setQuantity: (value: string): void => {
    const { maxQuantity } = get()
    let validationError: string | null = null
    const num = Number(value)
    if (value === '' || isNaN(num)) {
      validationError = 'Please enter a valid number.'
    } else if (num <= 0) {
      validationError = 'Quantity must be greater than zero.'
    } else if (maxQuantity !== null && num > maxQuantity) {
      validationError = `Quantity cannot exceed your holding of ${maxQuantity} shares.`
    }
    set({ quantity: value, validationError })
  },

  _confirmSell: async (onFilled: () => Promise<void>): Promise<void> => {
    set({ isSubmitting: true, submitError: null })
    const state = get()
    const accountId = usePortfolioStore.getState().selectedAccountId
    const user = useSessionStore.getState().user
    const userId = user?.userId ?? ''
    try {
      const response = await placeOrder(state.idempotencyKey!, {
        accountId: accountId ?? '',
        userId,
        ticker: state.ticker!,
        quantity: state.quantity,
        side: 'SELL',
        orderType: 'MARKET',
        priceSnapshot: String(state.priceSnapshot),
      })
      set({ result: response, isSubmitting: false })
      if (response.status === 'FILLED') {
        await onFilled()
      }
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        set({
          idempotencyKey: crypto.randomUUID(),
          submitError: 'Duplicate order detected. Please try again.',
          isSubmitting: false,
        })
      } else {
        set({ submitError: 'Something went wrong. Please try again.', isSubmitting: false })
      }
    }
  },
}))

export interface SellPanelHook extends SellPanelState {
  openSellPanel: (ticker: string, maxQuantity: number) => Promise<void>
  closeSellPanel: () => void
  setQuantity: (value: string) => void
  confirmSell: () => Promise<void>
}

export function useSellPanel(): SellPanelHook {
  const queryClient = useQueryClient()
  const store = useSellPanelStore()

  const confirmSell = async (): Promise<void> => {
    await store._confirmSell(async () => {
      await queryClient.invalidateQueries({ queryKey: [PORTFOLIO_HOLDINGS_KEY] })
      await queryClient.invalidateQueries({ queryKey: [ACCOUNTS_QUERY_KEY] })
    })
  }

  return {
    isOpen: store.isOpen,
    ticker: store.ticker,
    maxQuantity: store.maxQuantity,
    priceSnapshot: store.priceSnapshot,
    idempotencyKey: store.idempotencyKey,
    quantity: store.quantity,
    validationError: store.validationError,
    isFetchingPrice: store.isFetchingPrice,
    priceError: store.priceError,
    isSubmitting: store.isSubmitting,
    submitError: store.submitError,
    result: store.result,
    openSellPanel: store.openSellPanel,
    closeSellPanel: store.closeSellPanel,
    setQuantity: store.setQuantity,
    confirmSell,
  }
}
