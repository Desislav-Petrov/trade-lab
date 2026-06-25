export interface SubscriptionResponse {
  ticker: string
  companyName: string
}
export interface BulkAddSubscriptionsRequest {
  userId: string
  tickers: string[]
}
export interface BulkAddSubscriptionsResponse {
  subscriptions: SubscriptionResponse[]
}
export interface BulkRemoveSubscriptionsRequest {
  userId: string
  tickers: string[]
}
