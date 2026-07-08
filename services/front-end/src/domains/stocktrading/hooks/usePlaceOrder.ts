import { useMutation, useQueryClient } from '@tanstack/react-query'
import { placeOrder } from '../api/ordersApi'
import { ACCOUNTS_QUERY_KEY } from '../../ledger/api/accountApi'
import type { PlaceOrderRequest } from '../api/ordersApi'

type PlaceOrderVariables = { idempotencyKey: string } & PlaceOrderRequest

export function usePlaceOrder() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ idempotencyKey, ...request }: PlaceOrderVariables) =>
      placeOrder(idempotencyKey, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [ACCOUNTS_QUERY_KEY] })
    },
  })
}

export type { PlaceOrderVariables }
