import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'
import { router } from './router'
import { Toaster } from '../shared/components/Toaster'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Centralised query policy: avoid redundant refetches on every window
      // refocus and keep data briefly fresh. Hooks may override per-query.
      staleTime: 5_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Toaster />
    </QueryClientProvider>
  )
}

export default App
