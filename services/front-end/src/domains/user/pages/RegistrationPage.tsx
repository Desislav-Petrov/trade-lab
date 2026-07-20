import { useNavigate } from 'react-router-dom'
import { RegistrationForm } from '../components/RegistrationForm'

export function RegistrationPage() {
  const navigate = useNavigate()

  function handleSuccess() {
    navigate('/login', { state: { banner: 'Account created. Please log in.' } })
  }

  return (
    <main className="flex min-h-full items-center justify-center p-6">
      <div className="w-full max-w-sm rounded border border-[var(--color-border)] bg-[var(--color-surface)] p-8">
        <p className="mb-1 text-xs tracking-widest text-[var(--color-accent)]">TRADE-LAB</p>
        <h1 className="mb-6 text-sm font-medium text-[var(--color-text-primary)]">
          Create an account
        </h1>
        <RegistrationForm onSuccess={handleSuccess} />
      </div>
    </main>
  )
}
