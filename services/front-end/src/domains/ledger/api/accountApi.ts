import axiosInstance from '../../../shared/api/axiosInstance'
import type {
  OpenAccountRequest,
  AccountResponse,
  AccountListResponse,
  TopUpAccountRequest,
  TopUpAccountResponse,
} from '../types/account'

export const ACCOUNTS_QUERY_KEY = 'accounts'

export async function createAccount(request: OpenAccountRequest): Promise<AccountResponse> {
  const response = await axiosInstance.post<AccountResponse>('/v1/accounts', request)
  return response.data
}

export async function fetchAccounts(
  userId: string,
  status?: 'ACTIVE' | 'SUSPENDED' | 'CLOSED'
): Promise<AccountListResponse> {
  const params: { userId: string; status?: 'ACTIVE' | 'SUSPENDED' | 'CLOSED' } = { userId }
  if (status !== undefined) {
    params.status = status
  }
  const response = await axiosInstance.get<AccountListResponse>('/v1/accounts', { params })
  return response.data
}

export const TOP_UP_ACCOUNT_KEY = 'topUpAccount'

export async function topUpAccount(
  accountId: string,
  request: TopUpAccountRequest
): Promise<TopUpAccountResponse> {
  const response = await axiosInstance.post<TopUpAccountResponse>(
    `/v1/accounts/${accountId}/top-up`,
    request
  )
  return response.data
}
