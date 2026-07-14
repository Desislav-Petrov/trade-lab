import axiosInstance from '../../../shared/api/axiosInstance'

export interface PlaceOrderRequest {
  accountId: string
  userId: string
  ticker: string
  quantity: string
  side: 'BUY' | 'SELL'
  orderType: 'MARKET'
  priceSnapshot: string
}

export interface PlaceOrderResponse {
  orderId: string
  status: 'FILLED' | 'REJECTED'
  ticker: string
  quantity: string
  side: 'BUY' | 'SELL'
  executionPrice: string | null
  totalCost: string | null
  totalProceeds: number | null
  rejectionReason: string | null
  accountId: string
  createdAt: string
}

export const ORDERS_QUERY_KEY = ['orders'] as const

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
