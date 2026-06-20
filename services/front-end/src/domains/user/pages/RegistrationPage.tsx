import { useNavigate } from 'react-router-dom'
import { RegistrationForm } from '../components/RegistrationForm'

export function RegistrationPage() {
  const navigate = useNavigate()

  function handleSuccess() {
    navigate('/login')
  }

  return (
    <main>
      <h1>Create an account</h1>
      <RegistrationForm onSuccess={handleSuccess} />
    </main>
  )
}
