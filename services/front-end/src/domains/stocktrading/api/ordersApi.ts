import axiosInstance from '../../../shared/api/axiosInstance'

export interface PlaceOrderRequest {
  accountId: string
  userId: string
  ticker: string
  quantity: string
  orderType: 'MARKET'
  priceSnapshot: string
  side: 'BUY' | 'SELL'
}

export interface PlaceOrderResponse {
  orderId: string
  status: 'FILLED' | 'REJECTED'
  ticker: string
  quantity: string
  executionPrice: string | null
  totalCost: string | null
  totalProceeds: number | null
  side: 'BUY' | 'SELL'
  rejectionReason: string | null
  accountId: string
  createdAt: string
}

export interface IndicativePriceResponse {
  ticker: string
  indicativePrice: number
}

export const ORDERS_QUERY_KEY = ['orders'] as const

export async function fetchIndicativePrice(ticker: string): Promise<IndicativePriceResponse> {
  const response = await axiosInstance.get<IndicativePriceResponse>(
    '/v1/stock-orders/indicative-price',
    { params: { ticker } }
  )
  return response.data
}

export async function placeOrder(
  idempotencyKey: string,
  request: PlaceOrderRequest
): Promise<PlaceOrderResponse> {
  const response = await axiosInstance.post<PlaceOrderResponse>('/v1/stock-orders', request, {
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  })
  return response.data
}
