import axiosInstance from '../../../shared/api/axiosInstance'
import type {
  SubscriptionResponse,
  BulkAddSubscriptionsRequest,
  BulkAddSubscriptionsResponse,
  BulkRemoveSubscriptionsRequest,
} from '../types/subscription'

export const SUBSCRIPTIONS_QUERY_KEY = 'subscriptions'

export async function fetchSubscriptions(userId: string): Promise<SubscriptionResponse[]> {
  const response = await axiosInstance.get<SubscriptionResponse[]>('/v1/market-data/subscriptions', {
    params: { userId },
  })
  return response.data
}

export async function bulkAddSubscriptions(
  request: BulkAddSubscriptionsRequest
): Promise<BulkAddSubscriptionsResponse> {
  const response = await axiosInstance.post<BulkAddSubscriptionsResponse>(
    '/v1/market-data/subscriptions',
    request
  )
  return response.data
}

export async function bulkRemoveSubscriptions(
  request: BulkRemoveSubscriptionsRequest
): Promise<void> {
  await axiosInstance.delete('/v1/market-data/subscriptions', { data: request })
}
