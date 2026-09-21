import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'
import { router } from './router'
import { Toaster } from '../shared/components/Toaster'
import { AssistantChatPopup } from '../domains/agent/components/AssistantChatPopup'
import { AssistantWidget } from '../domains/agent/components/AssistantWidget'
import { useSessionStore } from '../domains/user/hooks/useSessionStore'

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
  const hasActiveSession = useSessionStore((state) => state.session !== null)

  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Toaster />
      {hasActiveSession && (
        <>
          <AssistantWidget />
          <AssistantChatPopup />
        </>
      )}
    </QueryClientProvider>
  )
}

export default App
