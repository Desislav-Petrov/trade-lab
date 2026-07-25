import { useNavigate } from 'react-router-dom'
import { AuthCallbackHandler } from '../components/AuthCallbackHandler'

export function AuthCallbackPage() {
  const navigate = useNavigate()

  function handleSuccess() {
    navigate('/trade', { replace: true })
  }

  return (
    <main className="flex min-h-full items-center justify-center p-6">
      <div className="w-full max-w-sm rounded border border-[var(--color-border)] bg-[var(--color-surface)] p-8">
        <p className="mb-4 text-xs tracking-widest text-[var(--color-accent)]">TRADE-LAB</p>
        <AuthCallbackHandler onSuccess={handleSuccess} />
      </div>
    </main>
  )
}
