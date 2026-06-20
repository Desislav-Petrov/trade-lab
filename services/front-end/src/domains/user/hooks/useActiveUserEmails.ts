import { useQuery } from '@tanstack/react-query'
import { getActiveUserEmails, ACTIVE_USER_EMAILS_KEY } from '../api/userApi'

export function useActiveUserEmails() {
  return useQuery({
    queryKey: ACTIVE_USER_EMAILS_KEY,
    queryFn: getActiveUserEmails,
  })
}
