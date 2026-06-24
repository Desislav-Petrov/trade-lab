export interface OpenAccountRequest {
  userId: string
  currency: 'USD' | 'GBP' | 'EUR'
  name?: string
}

export interface AccountResponse {
  accountId: string
  name: string
  currency: string
  balance: number
  status: string
  createdAt: string
}

export interface AccountListResponse {
  accounts: AccountResponse[]
}

export interface TopUpAccountRequest {
  userId: string
  amount: number
}

export interface TopUpAccountResponse {
  accountId: string
  newBalance: number
  currency: string
  ledgerEntryId: string
  timestamp: string
}
