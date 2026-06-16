import { createBrowserRouter } from 'react-router-dom'
import { RegistrationPage } from '../domains/user/pages/RegistrationPage'

export const router = createBrowserRouter([
  { path: '/register', element: <RegistrationPage /> },
])
