import { create } from 'zustand'
import { useQueryClient } from '@tanstack/react-query'
import { fetchIndicativePrice, placeOrder } from '../api/ordersApi'
import type { PlaceOrderResponse } from '../api/ordersApi'
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

interface SellPanelActions {
  openSellPanel: (ticker: string, maxQuantity: number) => Promise<void>
  closeSellPanel: () => void
  setQuantity: (value: string) => void
}

const initialState: SellPanelState = {
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

const useSellPanelStore = create<SellPanelState & SellPanelActions>((set, get) => ({
  ...initialState,

  openSellPanel: async (ticker: string, maxQuantity: number) => {
    set({ isFetchingPrice: true, priceError: null, ticker, maxQuantity })
    try {
      const { indicativePrice } = await fetchIndicativePrice(ticker)
      set({
        priceSnapshot: indicativePrice,
        idempotencyKey: crypto.randomUUID(),
        isOpen: true,
        isFetchingPrice: false,
        quantity: '',
        validationError: null,
        submitError: null,
        result: null,
      })
    } catch {
      set({ priceError: 'Could not fetch indicative price.', isFetchingPrice: false })
    }
  },

  closeSellPanel: () => set(initialState),

  setQuantity: (value: string) => {
    const { maxQuantity } = get()
    let validationError: string | null = null

    if (value === '' || isNaN(Number(value))) {
      validationError = 'Please enter a valid number.'
    } else {
      const num = parseFloat(value)
      if (num <= 0) {
        validationError = 'Quantity must be greater than zero.'
      } else if (maxQuantity !== null && num > maxQuantity) {
        validationError = `Quantity cannot exceed your holding of ${maxQuantity} shares.`
      }
    }

    set({ quantity: value, validationError })
  },
}))

export function useSellPanel() {
  const store = useSellPanelStore()
  const queryClient = useQueryClient()
  const accountId = usePortfolioStore((s) => s.selectedAccountId)
  const user = useSessionStore((s) => s.user)

  const confirmSell = async () => {
    const { ticker, quantity, priceSnapshot, idempotencyKey } = useSellPanelStore.getState()

    if (!ticker || !idempotencyKey || priceSnapshot === null || !accountId || !user) return

    useSellPanelStore.setState({ isSubmitting: true, submitError: null })

    try {
      const response = await placeOrder(idempotencyKey, {
        accountId,
        userId: user.userId,
        ticker,
        quantity,
        side: 'SELL',
        orderType: 'MARKET',
        priceSnapshot: String(priceSnapshot),
      })

      if (response.status === 'FILLED') {
        void queryClient.invalidateQueries({ queryKey: [PORTFOLIO_HOLDINGS_KEY] })
        void queryClient.invalidateQueries({ queryKey: [ACCOUNTS_QUERY_KEY] })
      }

      useSellPanelStore.setState({ result: response, isSubmitting: false })
    } catch (err) {
      const axiosError = err as { response?: { status: number } }
      if (axiosError?.response?.status === 409) {
        useSellPanelStore.setState({
          idempotencyKey: crypto.randomUUID(),
          submitError: 'Duplicate order detected. Please try again.',
          isSubmitting: false,
        })
      } else {
        useSellPanelStore.setState({
          submitError: 'Something went wrong. Please try again.',
          isSubmitting: false,
        })
      }
    }
  }

  return { ...store, confirmSell }
}
